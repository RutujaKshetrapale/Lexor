using FeedbackService.DTOs;
using FeedbackService.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace FeedbackService.Controllers;

[ApiController]
[Route("api/v1/drivers/me")]
[Authorize(Roles = "DRIVER")]
public class DriverFeedbackController : ControllerBase
{
    private readonly IFeedbackService _feedbackService;
    private readonly IRatingCalculationService _ratingCalculationService;

    public DriverFeedbackController(IFeedbackService feedbackService, IRatingCalculationService ratingCalculationService)
    {
        _feedbackService = feedbackService;
        _ratingCalculationService = ratingCalculationService;
    }

    [HttpPost("rides/{rideUuid}/rider-rating")]
    public async Task<ActionResult<ApiResponse<FeedbackResponse>>> SubmitRiderRating(
        [FromRoute] string rideUuid,
        [FromBody] SubmitRatingRequest request)
    {
        var authHeader = Request.Headers.Authorization.FirstOrDefault();
        var response = await _feedbackService.SubmitDriverFeedbackAsync(User, rideUuid, request, authHeader);
        return StatusCode(StatusCodes.Status201Created, ApiResponse<FeedbackResponse>.Ok(response, "Rider rating submitted successfully"));
    }

    [HttpGet("feedback")]
    public async Task<ActionResult<ApiResponse<PageResponse<FeedbackResponse>>>> GetReceivedFeedback(
        [FromQuery] int page = 0,
        [FromQuery] int size = 10)
    {
        var response = await _feedbackService.GetDriverReceivedFeedbackAsync(User, page, size);
        return Ok(ApiResponse<PageResponse<FeedbackResponse>>.Ok(response));
    }

    [HttpGet("rating-summary")]
    public async Task<ActionResult<ApiResponse<DriverRatingSummaryResponse>>> GetMyRatingSummary()
    {
        var driverUuid = User.FindFirst("sub")?.Value
            ?? User.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value
            ?? User.FindFirst("uuid")?.Value
            ?? string.Empty;

        var summary = await _ratingCalculationService.GetDriverSummaryAsync(driverUuid);
        return Ok(ApiResponse<DriverRatingSummaryResponse>.Ok(summary));
    }
}
