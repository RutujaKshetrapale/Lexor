using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using NotificationService.DTOs;
using NotificationService.Services;

namespace NotificationService.Controllers;

[ApiController]
[Route("api/v1/admin/notifications")]
[Authorize(Roles = "ADMIN")]
public class AdminNotificationsController : ControllerBase
{
    private readonly INotificationService _notificationService;

    public AdminNotificationsController(INotificationService notificationService)
    {
        _notificationService = notificationService;
    }

    [HttpGet]
    public async Task<ActionResult<ApiResponse<PageResponse<NotificationDto>>>> ListNotifications(
        [FromQuery] int page = 0,
        [FromQuery] int size = 20
    )
    {
        var result = await _notificationService.AdminListNotificationsAsync(page, size);
        return Ok(ApiResponse<PageResponse<NotificationDto>>.Ok(result));
    }

    [HttpGet("{notificationUuid}/audit")]
    public async Task<ActionResult<ApiResponse<List<DeliveryAuditDto>>>> GetDeliveryAudit(string notificationUuid)
    {
        var result = await _notificationService.AdminGetDeliveryAuditAsync(notificationUuid);
        return Ok(ApiResponse<List<DeliveryAuditDto>>.Ok(result));
    }

    [HttpPost("{notificationUuid}/retry")]
    public async Task<ActionResult<ApiResponse<NotificationDto>>> RetryDelivery(string notificationUuid)
    {
        var result = await _notificationService.AdminRetryDeliveryAsync(notificationUuid);
        return Ok(ApiResponse<NotificationDto>.Ok(result, "Delivery retry scheduled successfully"));
    }

    [HttpGet("metrics")]
    public async Task<ActionResult<ApiResponse<NotificationMetricsResponse>>> GetMetrics()
    {
        var result = await _notificationService.AdminGetMetricsAsync();
        return Ok(ApiResponse<NotificationMetricsResponse>.Ok(result));
    }
}
