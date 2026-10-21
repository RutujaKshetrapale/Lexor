using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using NotificationService.Domain.Enums;
using NotificationService.DTOs;
using NotificationService.Providers;
using NotificationService.Services;

namespace NotificationService.Controllers;

[ApiController]
[Route("api/v1/notifications/me")]
[Authorize]
public class UserNotificationsController : ControllerBase
{
    private readonly INotificationService _notificationService;
    private readonly IPreferenceService _preferenceService;
    private readonly IRealtimeNotifier _realtimeNotifier;

    public UserNotificationsController(
        INotificationService notificationService,
        IPreferenceService preferenceService,
        IRealtimeNotifier realtimeNotifier
    )
    {
        _notificationService = notificationService;
        _preferenceService = preferenceService;
        _realtimeNotifier = realtimeNotifier;
    }

    private string CurrentUserId =>
        User.FindFirstValue(ClaimTypes.NameIdentifier) ??
        User.FindFirstValue("sub") ??
        throw new UnauthorizedAccessException("Missing user identifier in JWT");

    [HttpGet]
    public async Task<ActionResult<ApiResponse<PageResponse<NotificationDto>>>> GetInbox(
        [FromQuery] bool? isRead,
        [FromQuery] NotificationCategory? category,
        [FromQuery] int page = 0,
        [FromQuery] int size = 20
    )
    {
        var result = await _notificationService.GetUserInboxAsync(CurrentUserId, isRead, category, page, size);
        return Ok(ApiResponse<PageResponse<NotificationDto>>.Ok(result));
    }

    [HttpGet("unread-count")]
    public async Task<ActionResult<ApiResponse<UnreadCountResponse>>> GetUnreadCount()
    {
        var result = await _notificationService.GetUnreadCountAsync(CurrentUserId);
        return Ok(ApiResponse<UnreadCountResponse>.Ok(result));
    }

    [HttpGet("{notificationUuid}")]
    public async Task<ActionResult<ApiResponse<NotificationDto>>> GetNotification(string notificationUuid)
    {
        var result = await _notificationService.GetNotificationAsync(CurrentUserId, notificationUuid);
        return Ok(ApiResponse<NotificationDto>.Ok(result));
    }

    [HttpPatch("{notificationUuid}/read")]
    public async Task<ActionResult<ApiResponse<NotificationDto>>> MarkAsRead(string notificationUuid)
    {
        var result = await _notificationService.MarkAsReadAsync(CurrentUserId, notificationUuid);
        return Ok(ApiResponse<NotificationDto>.Ok(result, "Notification marked as read"));
    }

    [HttpPatch("read-all")]
    public async Task<ActionResult<ApiResponse<int>>> MarkAllAsRead()
    {
        var count = await _notificationService.MarkAllAsReadAsync(CurrentUserId);
        return Ok(ApiResponse<int>.Ok(count, $"Marked {count} notifications as read"));
    }

    [HttpGet("preferences")]
    public async Task<ActionResult<ApiResponse<NotificationPreferenceDto>>> GetPreferences()
    {
        var result = await _preferenceService.GetPreferencesAsync(CurrentUserId);
        return Ok(ApiResponse<NotificationPreferenceDto>.Ok(result));
    }

    [HttpPut("preferences")]
    public async Task<ActionResult<ApiResponse<NotificationPreferenceDto>>> UpdatePreferences(
        [FromBody] UpdatePreferencesRequest request
    )
    {
        var result = await _preferenceService.UpdatePreferencesAsync(CurrentUserId, request);
        return Ok(ApiResponse<NotificationPreferenceDto>.Ok(result, "Preferences updated successfully"));
    }

    [HttpGet("stream")]
    public async Task GetStream(CancellationToken cancellationToken)
    {
        Response.Headers.Append("Content-Type", "text/event-stream");
        Response.Headers.Append("Cache-Control", "no-cache");
        Response.Headers.Append("Connection", "keep-alive");

        var reader = _realtimeNotifier.Subscribe(CurrentUserId);

        await Response.WriteAsync("event: INIT\ndata: Connected to LEXOR Realtime Notification Stream\n\n", cancellationToken);
        await Response.Body.FlushAsync(cancellationToken);

        try
        {
            await foreach (var notification in reader.ReadAllAsync(cancellationToken))
            {
                var json = System.Text.Json.JsonSerializer.Serialize(notification);
                await Response.WriteAsync($"event: NOTIFICATION\ndata: {json}\n\n", cancellationToken);
                await Response.Body.FlushAsync(cancellationToken);
            }
        }
        catch (OperationCanceledException)
        {
            // Client disconnected gracefully
        }
    }
}
