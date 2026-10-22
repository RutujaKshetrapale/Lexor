using FeedbackService.DTOs;
using FeedbackService.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace FeedbackService.Controllers;

[ApiController]
[AllowAnonymous]
public class RatingSummaryController : ControllerBase
{
    private readonly IRatingCalculationService _ratingCalculationService;

    public RatingSummaryController(IRatingCalculationService ratingCalculationService)
    {
        _ratingCalculationService = ratingCalculationService;
    }

    [HttpGet("api/v1/drivers/{driverUuid}/rating-summary")]
    [HttpGet("api/v1/feedback/drivers/{driverUuid}/rating-summary")]
    public async Task<ActionResult<ApiResponse<DriverRatingSummaryResponse>>> GetDriverRatingSummary([FromRoute] string driverUuid)
    {
        var summary = await _ratingCalculationService.GetDriverSummaryAsync(driverUuid);
        return Ok(ApiResponse<DriverRatingSummaryResponse>.Ok(summary));
    }

    [HttpGet("api/v1/riders/{riderUuid}/rating-summary")]
    [HttpGet("api/v1/feedback/riders/{riderUuid}/rating-summary")]
    public async Task<ActionResult<ApiResponse<RiderRatingSummaryResponse>>> GetRiderRatingSummary([FromRoute] string riderUuid)
    {
        var summary = await _ratingCalculationService.GetRiderSummaryAsync(riderUuid);
        return Ok(ApiResponse<RiderRatingSummaryResponse>.Ok(summary));
    }
}
