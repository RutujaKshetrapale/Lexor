using Microsoft.EntityFrameworkCore;
using NotificationService.Data;
using NotificationService.Domain.Entities;
using NotificationService.Domain.Enums;
using NotificationService.DTOs;
using NotificationService.Exceptions;
using NotificationService.Providers;

namespace NotificationService.Services;

public interface INotificationService
{
    Task<PageResponse<NotificationDto>> GetUserInboxAsync(
        string userUuid,
        bool? isRead,
        NotificationCategory? category,
        int page,
        int size
    );

    Task<UnreadCountResponse> GetUnreadCountAsync(string userUuid);

    Task<NotificationDto> GetNotificationAsync(string userUuid, string notificationUuid);

    Task<NotificationDto> MarkAsReadAsync(string userUuid, string notificationUuid);

    Task<int> MarkAllAsReadAsync(string userUuid);

    Task<NotificationDto> DispatchNotificationAsync(DirectNotificationRequest request);

    // Admin endpoints
    Task<PageResponse<NotificationDto>> AdminListNotificationsAsync(int page, int size);

    Task<List<DeliveryAuditDto>> AdminGetDeliveryAuditAsync(string notificationUuid);

    Task<NotificationDto> AdminRetryDeliveryAsync(string notificationUuid);

    Task<NotificationMetricsResponse> AdminGetMetricsAsync();
}

public class NotificationService : INotificationService
{
    private readonly NotificationDbContext _dbContext;
    private readonly IPreferenceService _preferenceService;
    private readonly IDeviceService _deviceService;
    private readonly IPushNotificationProvider _pushProvider;
    private readonly IRealtimeNotifier _realtimeNotifier;
    private readonly ILogger<NotificationService> _logger;

    public NotificationService(
        NotificationDbContext dbContext,
        IPreferenceService preferenceService,
        IDeviceService deviceService,
        IPushNotificationProvider pushProvider,
        IRealtimeNotifier realtimeNotifier,
        ILogger<NotificationService> logger
    )
    {
        _dbContext = dbContext;
        _preferenceService = preferenceService;
        _deviceService = deviceService;
        _pushProvider = pushProvider;
        _realtimeNotifier = realtimeNotifier;
        _logger = logger;
    }

    public async Task<PageResponse<NotificationDto>> GetUserInboxAsync(
        string userUuid,
        bool? isRead,
        NotificationCategory? category,
        int page,
        int size
    )
    {
        int boundedSize = Math.Clamp(size, 1, 50);
        int boundedPage = Math.Max(page, 0);

        var query = _dbContext.Notifications
            .AsNoTracking()
            .Where(n => n.RecipientUuid == userUuid);

        if (isRead.HasValue) query = query.Where(n => n.IsRead == isRead.Value);
        if (category.HasValue) query = query.Where(n => n.Category == category.Value);

        var total = await query.CountAsync();
        var items = await query
            .OrderByDescending(n => n.CreatedAt)
            .Skip(boundedPage * boundedSize)
            .Take(boundedSize)
            .ToListAsync();

        var dtos = items.Select(MapToDto).ToList();
        var totalPages = (int)Math.Ceiling(total / (double)boundedSize);

        return new PageResponse<NotificationDto>(
            dtos,
            boundedPage,
            boundedSize,
            total,
            totalPages,
            boundedPage >= totalPages - 1
        );
    }

    public async Task<UnreadCountResponse> GetUnreadCountAsync(string userUuid)
    {
        var count = await _dbContext.Notifications
            .AsNoTracking()
            .CountAsync(n => n.RecipientUuid == userUuid && !n.IsRead);

        return new UnreadCountResponse(userUuid, count);
    }

    public async Task<NotificationDto> GetNotificationAsync(string userUuid, string notificationUuid)
    {
        var notification = await _dbContext.Notifications
            .AsNoTracking()
            .FirstOrDefaultAsync(n => n.NotificationUuid == notificationUuid);

        if (notification == null)
        {
            throw new ResourceNotFoundException($"Notification not found: {notificationUuid}");
        }

        if (notification.RecipientUuid != userUuid)
        {
            throw new ForbiddenException("Cannot access notification belonging to another user");
        }

        return MapToDto(notification);
    }

    public async Task<NotificationDto> MarkAsReadAsync(string userUuid, string notificationUuid)
    {
        var notification = await _dbContext.Notifications
            .FirstOrDefaultAsync(n => n.NotificationUuid == notificationUuid);

        if (notification == null)
        {
            throw new ResourceNotFoundException($"Notification not found: {notificationUuid}");
        }

        if (notification.RecipientUuid != userUuid)
        {
            throw new ForbiddenException("Cannot modify notification belonging to another user");
        }

        if (!notification.IsRead)
        {
            notification.IsRead = true;
            notification.ReadAt = DateTime.UtcNow;
            notification.UpdatedAt = DateTime.UtcNow;
            await _dbContext.SaveChangesAsync();
            _logger.LogInformation("Marked notification {Uuid} as read for user {UserUuid}", notificationUuid, userUuid);
        }

        return MapToDto(notification);
    }

    public async Task<int> MarkAllAsReadAsync(string userUuid)
    {
        var unread = await _dbContext.Notifications
            .Where(n => n.RecipientUuid == userUuid && !n.IsRead)
            .ToListAsync();

        var now = DateTime.UtcNow;
        foreach (var n in unread)
        {
            n.IsRead = true;
            n.ReadAt = now;
            n.UpdatedAt = now;
        }

        await _dbContext.SaveChangesAsync();
        _logger.LogInformation("Marked {Count} notifications as read for user {UserUuid}", unread.Count, userUuid);
        return unread.Count;
    }

    public async Task<NotificationDto> DispatchNotificationAsync(DirectNotificationRequest request)
    {
        var notification = new Notification
        {
            NotificationUuid = Guid.NewGuid().ToString(),
            RecipientUuid = request.RecipientUuid,
            RecipientRole = request.RecipientRole,
            NotificationType = request.NotificationType,
            Category = request.Category,
            Title = request.Title,
            Body = request.Body,
            DeepLink = request.DeepLink,
            RelatedEntityType = request.RelatedEntityType,
            RelatedEntityUuid = request.RelatedEntityUuid,
            Status = NotificationStatus.PROCESSING,
            IdempotencyKey = request.IdempotencyKey,
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        _dbContext.Notifications.Add(notification);
        await _dbContext.SaveChangesAsync();

        var preferences = await _preferenceService.GetOrCreateEntityAsync(request.RecipientUuid);
        var channels = request.Channels ?? [NotificationChannel.IN_APP, NotificationChannel.PUSH];

        // In-App Delivery
        if (channels.Contains(NotificationChannel.IN_APP))
        {
            var inAppDelivery = new NotificationDelivery
            {
                DeliveryUuid = Guid.NewGuid().ToString(),
                NotificationUuid = notification.NotificationUuid,
                Channel = NotificationChannel.IN_APP,
                Provider = "IN_APP",
                RecipientDestination = notification.RecipientUuid,
                Status = DeliveryStatus.DELIVERED,
                CompletedAt = DateTime.UtcNow,
                CreatedAt = DateTime.UtcNow,
                UpdatedAt = DateTime.UtcNow
            };
            _dbContext.NotificationDeliveries.Add(inAppDelivery);
        }

        // Push / FCM Delivery
        if (channels.Contains(NotificationChannel.PUSH) && preferences.PushEnabled)
        {
            var devices = await _deviceService.GetActiveDeviceEntitiesAsync(request.RecipientUuid);
            foreach (var device in devices)
            {
                var result = _pushProvider.SendPushNotification(
                    device.FcmToken,
                    device.Platform,
                    request.Title,
                    request.Body,
                    request.DeepLink,
                    new Dictionary<string, string>
                    {
                        ["notificationUuid"] = notification.NotificationUuid,
                        ["type"] = request.NotificationType
                    }
                );

                var delivery = new NotificationDelivery
                {
                    DeliveryUuid = Guid.NewGuid().ToString(),
                    NotificationUuid = notification.NotificationUuid,
                    Channel = NotificationChannel.PUSH,
                    Provider = _pushProvider.ProviderName,
                    RecipientDestination = MaskToken(device.FcmToken),
                    Status = result.Status,
                    ProviderMessageId = result.ProviderMessageId,
                    ErrorCode = result.ErrorCode,
                    ErrorMessage = result.ErrorMessage,
                    CompletedAt = result.Successful ? DateTime.UtcNow : null,
                    CreatedAt = DateTime.UtcNow,
                    UpdatedAt = DateTime.UtcNow
                };
                _dbContext.NotificationDeliveries.Add(delivery);

                if (!result.Successful && _pushProvider.IsTokenInvalidError(result.ErrorCode))
                {
                    await _deviceService.DeactivateInvalidTokenAsync(device.FcmToken);
                }
            }
        }

        notification.Status = NotificationStatus.COMPLETED;
        notification.UpdatedAt = DateTime.UtcNow;
        await _dbContext.SaveChangesAsync();

        var dto = MapToDto(notification);

        // Real-time notification forward
        _realtimeNotifier.NotifyUser(request.RecipientUuid, dto);

        return dto;
    }

    public async Task<PageResponse<NotificationDto>> AdminListNotificationsAsync(int page, int size)
    {
        int boundedSize = Math.Clamp(size, 1, 50);
        int boundedPage = Math.Max(page, 0);

        var query = _dbContext.Notifications.AsNoTracking();
        var total = await query.CountAsync();
        var items = await query
            .OrderByDescending(n => n.CreatedAt)
            .Skip(boundedPage * boundedSize)
            .Take(boundedSize)
            .ToListAsync();

        var dtos = items.Select(MapToDto).ToList();
        var totalPages = (int)Math.Ceiling(total / (double)boundedSize);

        return new PageResponse<NotificationDto>(
            dtos,
            boundedPage,
            boundedSize,
            total,
            totalPages,
            boundedPage >= totalPages - 1
        );
    }

    public async Task<List<DeliveryAuditDto>> AdminGetDeliveryAuditAsync(string notificationUuid)
    {
        var list = await _dbContext.NotificationDeliveries
            .AsNoTracking()
            .Where(d => d.NotificationUuid == notificationUuid)
            .ToListAsync();

        return list.Select(d => new DeliveryAuditDto(
            d.DeliveryUuid,
            d.NotificationUuid,
            d.Channel,
            d.Provider,
            d.RecipientDestination,
            d.AttemptNumber,
            d.ProviderMessageId,
            d.Status,
            d.ErrorCode,
            d.ErrorMessage,
            d.AttemptedAt,
            d.CompletedAt
        )).ToList();
    }

    public async Task<NotificationDto> AdminRetryDeliveryAsync(string notificationUuid)
    {
        var notification = await _dbContext.Notifications
            .FirstOrDefaultAsync(n => n.NotificationUuid == notificationUuid);

        if (notification == null)
        {
            throw new ResourceNotFoundException($"Notification not found: {notificationUuid}");
        }

        var deliveries = await _dbContext.NotificationDeliveries
            .Where(d => d.NotificationUuid == notificationUuid)
            .ToListAsync();

        foreach (var delivery in deliveries)
        {
            if (delivery.Status is DeliveryStatus.FAILED or DeliveryStatus.RETRY_SCHEDULED)
            {
                delivery.AttemptNumber++;
                delivery.Status = DeliveryStatus.ACCEPTED;
                delivery.ErrorMessage = null;
                delivery.ErrorCode = null;
                delivery.CompletedAt = DateTime.UtcNow;
                delivery.UpdatedAt = DateTime.UtcNow;
            }
        }

        notification.Status = NotificationStatus.COMPLETED;
        notification.UpdatedAt = DateTime.UtcNow;
        await _dbContext.SaveChangesAsync();

        _logger.LogInformation("Admin retried notification deliveries for {Uuid}", notificationUuid);
        return MapToDto(notification);
    }

    public async Task<NotificationMetricsResponse> AdminGetMetricsAsync()
    {
        var totalNotifs = await _dbContext.Notifications.CountAsync();
        var totalDelivs = await _dbContext.NotificationDeliveries.CountAsync();
        var delivered = await _dbContext.NotificationDeliveries.CountAsync(d => d.Status == DeliveryStatus.DELIVERED);
        var failed = await _dbContext.NotificationDeliveries.CountAsync(d => d.Status == DeliveryStatus.FAILED || d.Status == DeliveryStatus.PERMANENTLY_FAILED);
        var pending = await _dbContext.NotificationDeliveries.CountAsync(d => d.Status == DeliveryStatus.PENDING || d.Status == DeliveryStatus.RETRY_SCHEDULED);
        var activeDevs = await _dbContext.NotificationDevices.CountAsync(d => d.IsActive);
        var deadLetter = await _dbContext.NotificationEventInbox.CountAsync(e => e.Status == EventInboxStatus.DEAD_LETTER);

        var channelBreakdown = new Dictionary<string, long>();
        foreach (NotificationChannel channel in Enum.GetValues(typeof(NotificationChannel)))
        {
            channelBreakdown[channel.ToString()] = await _dbContext.NotificationDeliveries.CountAsync(d => d.Channel == channel);
        }

        return new NotificationMetricsResponse(
            totalNotifs,
            totalDelivs,
            delivered,
            failed,
            pending,
            activeDevs,
            deadLetter,
            channelBreakdown
        );
    }

    private static NotificationDto MapToDto(Notification entity) => new(
        entity.NotificationUuid,
        entity.RecipientUuid,
        entity.RecipientRole,
        entity.NotificationType,
        entity.Category,
        entity.Title,
        entity.Body,
        entity.DeepLink,
        entity.RelatedEntityType,
        entity.RelatedEntityUuid,
        entity.IsRead,
        entity.ReadAt,
        entity.Status,
        entity.CreatedAt
    );

    private static string MaskToken(string? token) =>
        string.IsNullOrEmpty(token) || token.Length < 10
            ? "***"
            : $"{token[..4]}...{token[^4..]}";
}
