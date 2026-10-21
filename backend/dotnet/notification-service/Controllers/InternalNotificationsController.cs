using Microsoft.AspNetCore.Mvc;
using NotificationService.DTOs;
using NotificationService.Exceptions;
using NotificationService.Services;

namespace NotificationService.Controllers;

[ApiController]
[Route("api/v1/internal/notifications")]
public class InternalNotificationsController : ControllerBase
{
    private readonly IEventIngestionService _eventIngestionService;
    private readonly INotificationService _notificationService;
    private readonly IOtpDeliveryService _otpDeliveryService;
    private readonly IConfiguration _configuration;

    public InternalNotificationsController(
        IEventIngestionService eventIngestionService,
        INotificationService notificationService,
        IOtpDeliveryService otpDeliveryService,
        IConfiguration configuration
    )
    {
        _eventIngestionService = eventIngestionService;
        _notificationService = notificationService;
        _otpDeliveryService = otpDeliveryService;
        _configuration = configuration;
    }

    private void ValidateInternalKey()
    {
        var expectedKey = _configuration["InternalServiceKey"] ?? "lexor-internal-service-secret-key-2026";
        var providedKey = Request.Headers["X-Internal-Service-Key"].FirstOrDefault();

        // Also permit if authenticated with ROLE_INTERNAL or ROLE_ADMIN
        if (User.Identity?.IsAuthenticated == true && (User.IsInRole("INTERNAL") || User.IsInRole("ADMIN")))
        {
            return;
        }

        if (string.IsNullOrEmpty(providedKey) || providedKey != expectedKey)
        {
            throw new ForbiddenException("Invalid or missing X-Internal-Service-Key header");
        }
    }

    [HttpPost("events")]
    public async Task<ActionResult<ApiResponse<NotificationDto>>> IngestEvent([FromBody] BusinessEventRequest request)
    {
        ValidateInternalKey();
        var result = await _eventIngestionService.IngestEventAsync(request);
        return StatusCode(StatusCodes.Status202Accepted, ApiResponse<NotificationDto>.Ok(result, "Event accepted and notification dispatched"));
    }

    [HttpPost("dispatch")]
    public async Task<ActionResult<ApiResponse<NotificationDto>>> DispatchNotification([FromBody] DirectNotificationRequest request)
    {
        ValidateInternalKey();
        var result = await _notificationService.DispatchNotificationAsync(request);
        return StatusCode(StatusCodes.Status201Created, ApiResponse<NotificationDto>.Ok(result, "Notification dispatched successfully"));
    }

    [HttpPost("otp")]
    public ActionResult<ApiResponse<string>> DeliverOtp([FromBody] OtpDeliveryRequest request)
    {
        ValidateInternalKey();
        var result = _otpDeliveryService.DeliverOtp(request);
        return Ok(result);
    }
}
