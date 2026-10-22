using FeedbackService.Domain.Enums;
using FeedbackService.DTOs;
using FeedbackService.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace FeedbackService.Controllers;

[ApiController]
[Route("api/v1/admin/feedback")]
[Authorize(Roles = "ADMIN")]
public class AdminFeedbackController : ControllerBase
{
    private readonly IFeedbackService _feedbackService;
    private readonly IModerationService _moderationService;

    public AdminFeedbackController(IFeedbackService feedbackService, IModerationService moderationService)
    {
        _feedbackService = feedbackService;
        _moderationService = moderationService;
    }

    [HttpGet]
    public async Task<ActionResult<ApiResponse<PageResponse<FeedbackResponse>>>> GetAdminFeedback(
        [FromQuery] string? reviewerUuid,
        [FromQuery] string? targetUuid,
        [FromQuery] ModerationStatus? status,
        [FromQuery] int page = 0,
        [FromQuery] int size = 20)
    {
        var response = await _feedbackService.GetAdminFeedbackAsync(reviewerUuid, targetUuid, status, page, size);
        return Ok(ApiResponse<PageResponse<FeedbackResponse>>.Ok(response));
    }

    [HttpGet("{reviewUuid}")]
    public async Task<ActionResult<ApiResponse<FeedbackResponse>>> GetAdminFeedbackByUuid([FromRoute] string reviewUuid)
    {
        var response = await _feedbackService.GetAdminFeedbackByUuidAsync(reviewUuid);
        return Ok(ApiResponse<FeedbackResponse>.Ok(response));
    }

    [HttpGet("reports")]
    public async Task<ActionResult<ApiResponse<PageResponse<FeedbackReportResponse>>>> GetReports(
        [FromQuery] ReportStatus? status,
        [FromQuery] int page = 0,
        [FromQuery] int size = 20)
    {
        var response = await _moderationService.GetReportsAsync(status, page, size);
        return Ok(ApiResponse<PageResponse<FeedbackReportResponse>>.Ok(response));
    }

    [HttpPost("{reviewUuid}/moderation")]
    public async Task<ActionResult<ApiResponse<FeedbackResponse>>> ApplyModeration(
        [FromRoute] string reviewUuid,
        [FromBody] ModerationActionRequest request)
    {
        var response = await _moderationService.ApplyModerationActionAsync(User, reviewUuid, request);
        return Ok(ApiResponse<FeedbackResponse>.Ok(response, "Moderation action executed successfully"));
    }

    [HttpGet("{reviewUuid}/history")]
    public async Task<ActionResult<ApiResponse<List<ModerationHistoryResponse>>>> GetModerationHistory([FromRoute] string reviewUuid)
    {
        var history = await _moderationService.GetModerationHistoryAsync(reviewUuid);
        return Ok(ApiResponse<List<ModerationHistoryResponse>>.Ok(history));
    }

    [HttpGet("analytics")]
    public async Task<ActionResult<ApiResponse<FeedbackAnalyticsResponse>>> GetAnalytics()
    {
        var analytics = await _moderationService.GetAnalyticsAsync();
        return Ok(ApiResponse<FeedbackAnalyticsResponse>.Ok(analytics));
    }
}
