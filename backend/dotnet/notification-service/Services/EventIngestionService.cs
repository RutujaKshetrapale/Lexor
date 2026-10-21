using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using NotificationService.Data;
using NotificationService.Domain.Entities;
using NotificationService.Domain.Enums;
using NotificationService.DTOs;
using NotificationService.Exceptions;

namespace NotificationService.Services;

public interface IEventIngestionService
{
    Task<NotificationDto> IngestEventAsync(BusinessEventRequest request);
}

public class EventIngestionService : IEventIngestionService
{
    private readonly NotificationDbContext _dbContext;
    private readonly INotificationService _notificationService;
    private readonly IPreferenceService _preferenceService;
    private readonly ITemplateService _templateService;
    private readonly ILogger<EventIngestionService> _logger;

    public EventIngestionService(
        NotificationDbContext dbContext,
        INotificationService notificationService,
        IPreferenceService preferenceService,
        ITemplateService templateService,
        ILogger<EventIngestionService> logger
    )
    {
        _dbContext = dbContext;
        _notificationService = notificationService;
        _preferenceService = preferenceService;
        _templateService = templateService;
        _logger = logger;
    }

    public async Task<NotificationDto> IngestEventAsync(BusinessEventRequest request)
    {
        // 1. Idempotent Deduplication Check
        var exists = await _dbContext.NotificationEventInbox.AnyAsync(e => e.EventId == request.EventId);
        if (exists)
        {
            _logger.LogWarning("Duplicate business event received with eventId: {EventId}", request.EventId);
            throw new DuplicateEventException($"Duplicate event: {request.EventId} has already been processed");
        }

        var payloadJson = JsonSerializer.Serialize(request);

        var inbox = new NotificationEventInbox
        {
            EventUuid = Guid.NewGuid().ToString(),
            EventId = request.EventId,
            EventType = request.EventType,
            SourceService = request.SourceService,
            Payload = payloadJson,
            Status = EventInboxStatus.PROCESSING,
            ReceivedAt = DateTime.UtcNow
        };

        _dbContext.NotificationEventInbox.Add(inbox);
        await _dbContext.SaveChangesAsync();

        try
        {
            // 2. Resolve recipient
            var recipientUuid = request.RecipientUuid;
            if (string.IsNullOrEmpty(recipientUuid) && request.Metadata != null)
            {
                if (request.Metadata.TryGetValue("riderUuid", out var rVal)) recipientUuid = rVal.ToString();
                else if (request.Metadata.TryGetValue("driverUuid", out var dVal)) recipientUuid = dVal.ToString();
            }
            recipientUuid ??= "SYSTEM_BROADCAST";

            var role = RecipientRole.RIDER;
            if (!string.IsNullOrEmpty(request.RecipientRole) && Enum.TryParse<RecipientRole>(request.RecipientRole, true, out var parsedRole))
            {
                role = parsedRole;
            }

            var pref = await _preferenceService.GetOrCreateEntityAsync(recipientUuid);
            var locale = pref.PreferredLocale;

            var vars = request.Metadata ?? new Dictionary<string, object>();
            var templateCode = MapEventToTemplateCode(request.EventType);
            var category = MapEventToCategory(request.EventType);

            var inAppTpl = await _templateService.RenderAsync(templateCode, NotificationChannel.IN_APP, locale, vars);

            var dispatchReq = new DirectNotificationRequest(
                recipientUuid,
                role,
                templateCode,
                category,
                inAppTpl.Title,
                inAppTpl.Body,
                $"/rides/{request.RelatedEntityUuid}",
                request.RelatedEntityType,
                request.RelatedEntityUuid,
                $"EVT-{request.EventId}",
                [NotificationChannel.IN_APP, NotificationChannel.PUSH]
            );

            var result = await _notificationService.DispatchNotificationAsync(dispatchReq);

            inbox.Status = EventInboxStatus.PROCESSED;
            inbox.ProcessedAt = DateTime.UtcNow;
            await _dbContext.SaveChangesAsync();

            _logger.LogInformation("Successfully processed business event {EventId} (type={EventType})",
                request.EventId, request.EventType);

            return result;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to process event {EventId}: {Message}", request.EventId, ex.Message);
            inbox.Status = EventInboxStatus.FAILED;
            inbox.ErrorMessage = ex.Message;
            inbox.RetryCount++;
            await _dbContext.SaveChangesAsync();
            throw;
        }
    }

    private static string MapEventToTemplateCode(string? eventType) => eventType switch
    {
        "RideRequestCreated" => "RIDE_REQUEST_CREATED",
        "DriverMatched" => "DRIVER_MATCHED",
        "DriverAssigned" => "DRIVER_ASSIGNED",
        "DriverArrived" => "DRIVER_ARRIVED",
        "RideStarted" => "RIDE_STARTED",
        "RideCompleted" => "RIDE_COMPLETED",
        "RideCancelled" => "RIDE_CANCELLED",
        "PaymentSucceeded" => "PAYMENT_SUCCEEDED",
        "PaymentFailed" => "PAYMENT_FAILED",
        "RefundInitiated" => "REFUND_INITIATED",
        "RefundCompleted" => "REFUND_COMPLETED",
        "DriverOfferCreated" => "DRIVER_OFFER",
        "UserSecurityEvent" => "SECURITY_ALERT",
        _ => eventType?.ToUpperInvariant() ?? "GENERAL_ALERT"
    };

    private static NotificationCategory MapEventToCategory(string? eventType)
    {
        if (string.IsNullOrEmpty(eventType)) return NotificationCategory.SYSTEM;
        if (eventType.StartsWith("Payment") || eventType.StartsWith("Refund")) return NotificationCategory.PAYMENT;
        if (eventType.Contains("Security")) return NotificationCategory.SECURITY;
        if (eventType.StartsWith("Ride") || eventType.StartsWith("Driver")) return NotificationCategory.RIDE;
        return NotificationCategory.SYSTEM;
    }
}
