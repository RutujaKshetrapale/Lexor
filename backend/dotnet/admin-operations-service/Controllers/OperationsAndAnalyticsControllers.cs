using System.Security.Claims;
using System.Text;
using AdminOperationsService.DTOs;
using AdminOperationsService.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace AdminOperationsService.Controllers;

[ApiController]
[Route("api/v1/admin/operations")]
[Authorize(Roles = "ROLE_ADMIN,ADMIN")]
public class AdminOperationsController : ControllerBase
{
    private readonly IOperationsDashboardService _dashboardService;

    public AdminOperationsController(IOperationsDashboardService dashboardService)
    {
        _dashboardService = dashboardService;
    }

    [HttpGet("overview")]
    public async Task<ActionResult<ApiResponse<ExecutiveOverviewResponse>>> GetOverview(
        [FromQuery] string timeRange = "TODAY",
        [FromQuery] DateTime? startDate = null,
        [FromQuery] DateTime? endDate = null)
    {
        var result = await _dashboardService.GetExecutiveOverviewAsync(timeRange, startDate, endDate);
        return Ok(ApiResponse<ExecutiveOverviewResponse>.Ok(result));
    }

    [HttpGet("live")]
    public async Task<ActionResult<ApiResponse<LiveOperationsSnapshotResponse>>> GetLiveSnapshot()
    {
        var result = await _dashboardService.GetLiveOperationsSnapshotAsync();
        return Ok(ApiResponse<LiveOperationsSnapshotResponse>.Ok(result));
    }

    [HttpPost("actions/cancel-ride")]
    public async Task<ActionResult<ApiResponse<string>>> EmergencyCancelRide([FromBody] EmergencyCancelRideRequest request)
    {
        await _dashboardService.EmergencyCancelRideAsync(User, request);
        return Ok(ApiResponse<string>.Ok($"Booking {request.BookingUuid} cancelled by administrator"));
    }
}

[ApiController]
[Route("api/v1/admin/analytics")]
[Authorize(Roles = "ROLE_ADMIN,ADMIN")]
public class AdminAnalyticsController : ControllerBase
{
    private readonly IPlatformAnalyticsService _analyticsService;

    public AdminAnalyticsController(IPlatformAnalyticsService analyticsService)
    {
        _analyticsService = analyticsService;
    }

    [HttpGet("trends")]
    public async Task<ActionResult<ApiResponse<PlatformTrendsResponse>>> GetTrends([FromQuery] string timeRange = "7D")
    {
        var result = await _analyticsService.GetPlatformTrendsAsync(timeRange);
        return Ok(ApiResponse<PlatformTrendsResponse>.Ok(result));
    }

    [HttpGet("export/csv")]
    public async Task<IActionResult> ExportCsv(
        [FromQuery] string entityType = "RIDES",
        [FromQuery] string timeRange = "7D",
        [FromQuery] DateTime? startDate = null,
        [FromQuery] DateTime? endDate = null)
    {
        var memoryStream = new MemoryStream();
        using (var writer = new StreamWriter(memoryStream, Encoding.UTF8, 1024, leaveOpen: true))
        {
            await _analyticsService.ExportCsvAsync(entityType, timeRange, startDate, endDate, writer);
        }

        memoryStream.Position = 0;
        return File(memoryStream, "text/csv", $"lexor-{entityType.ToLower()}-export.csv");
    }
}
