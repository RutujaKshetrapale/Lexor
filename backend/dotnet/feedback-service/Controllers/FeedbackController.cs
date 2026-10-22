using FeedbackService.DTOs;
using FeedbackService.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace FeedbackService.Controllers;

[ApiController]
[Route("api/v1/feedback")]
public class FeedbackController : ControllerBase
{
    private readonly IFeedbackService _feedbackService;
    private readonly IModerationService _moderationService;

    public FeedbackController(IFeedbackService feedbackService, IModerationService moderationService)
    {
        _feedbackService = feedbackService;
        _moderationService = moderationService;
    }

    [HttpPost("rides/{rideUuid}/driver-rating")]
    [Authorize(Roles = "RIDER")]
    public async Task<ActionResult<ApiResponse<FeedbackResponse>>> SubmitRiderRating(
        [FromRoute] string rideUuid,
        [FromBody] SubmitRatingRequest request)
    {
        var authHeader = Request.Headers.Authorization.FirstOrDefault();
        var response = await _feedbackService.SubmitRiderFeedbackAsync(User, rideUuid, request, authHeader);
        return StatusCode(StatusCodes.Status201Created, ApiResponse<FeedbackResponse>.Ok(response, "Driver rating submitted successfully"));
    }

    [HttpPost("rides/{rideUuid}/rider-rating")]
    [Authorize(Roles = "DRIVER")]
    public async Task<ActionResult<ApiResponse<FeedbackResponse>>> SubmitDriverRatingAlias(
        [FromRoute] string rideUuid,
        [FromBody] SubmitRatingRequest request)
    {
        var authHeader = Request.Headers.Authorization.FirstOrDefault();
        var response = await _feedbackService.SubmitDriverFeedbackAsync(User, rideUuid, request, authHeader);
        return StatusCode(StatusCodes.Status201Created, ApiResponse<FeedbackResponse>.Ok(response, "Rider rating submitted successfully"));
    }

    [HttpGet("me/submitted")]
    [Authorize]
    public async Task<ActionResult<ApiResponse<PageResponse<FeedbackResponse>>>> GetMySubmittedReviews(
        [FromQuery] int page = 0,
        [FromQuery] int size = 10)
    {
        var response = await _feedbackService.GetMySubmittedFeedbackAsync(User, page, size);
        return Ok(ApiResponse<PageResponse<FeedbackResponse>>.Ok(response));
    }

    [HttpGet("me/pending")]
    [Authorize]
    public async Task<ActionResult<ApiResponse<List<PendingFeedbackItemResponse>>>> GetMyPendingFeedback()
    {
        var authHeader = Request.Headers.Authorization.FirstOrDefault();
        var response = await _feedbackService.GetMyPendingFeedbackAsync(User, authHeader);
        return Ok(ApiResponse<List<PendingFeedbackItemResponse>>.Ok(response));
    }

    [HttpGet("me/reviews/{reviewUuid}")]
    [Authorize]
    public async Task<ActionResult<ApiResponse<FeedbackResponse>>> GetMyReviewByUuid([FromRoute] string reviewUuid)
    {
        var response = await _feedbackService.GetMyReviewByUuidAsync(User, reviewUuid);
        return Ok(ApiResponse<FeedbackResponse>.Ok(response));
    }

    [HttpPatch("me/reviews/{reviewUuid}")]
    [Authorize]
    public async Task<ActionResult<ApiResponse<FeedbackResponse>>> UpdateMyReview(
        [FromRoute] string reviewUuid,
        [FromBody] UpdateRatingRequest request)
    {
        var response = await _feedbackService.UpdateMyReviewAsync(User, reviewUuid, request);
        return Ok(ApiResponse<FeedbackResponse>.Ok(response, "Review updated successfully"));
    }

    [HttpPost("reviews/{reviewUuid}/report")]
    [Authorize]
    public async Task<ActionResult<ApiResponse<FeedbackReportResponse>>> ReportReview(
        [FromRoute] string reviewUuid,
        [FromBody] ReportReviewRequest request)
    {
        var response = await _moderationService.ReportReviewAsync(User, reviewUuid, request);
        return StatusCode(StatusCodes.Status201Created,
            ApiResponse<FeedbackReportResponse>.Ok(response, "Report submitted successfully and queued for administrative review"));
    }

    [HttpGet("tags")]
    [AllowAnonymous]
    public async Task<ActionResult<ApiResponse<List<FeedbackTagDto>>>> GetFeedbackTags([FromQuery] string? targetRole)
    {
        var tags = await _feedbackService.GetFeedbackTagsAsync(targetRole);
        return Ok(ApiResponse<List<FeedbackTagDto>>.Ok(tags));
    }
}
