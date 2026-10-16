using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using RideRequestService.DTOs.Response;
using RideRequestService.Enums;
using RideRequestService.Services;

namespace RideRequestService.Controllers;

[ApiController]
[Route("api/v1/admin/ride-requests")]
[Authorize(Roles = "ADMIN,ROLE_ADMIN")]
public class AdminRideRequestController : ControllerBase
{
    private readonly IAdminRideRequestService _adminRideRequestService;

    public AdminRideRequestController(IAdminRideRequestService adminRideRequestService)
    {
        _adminRideRequestService = adminRideRequestService;
    }

    /// <summary>
    /// Admin: List all ride requests
    /// </summary>
    [HttpGet]
    [ProducesResponseType(typeof(PageResponse<RideRequestResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    public async Task<IActionResult> GetAllRideRequests(
        [FromQuery] RideRequestStatus? status,
        [FromQuery] long? riderId,
        [FromQuery] int page = 0,
        [FromQuery] int size = 20)
    {
        var response = await _adminRideRequestService.GetAllRideRequestsAsync(page, size, status, riderId);
        return Ok(response);
    }

    /// <summary>
    /// Admin: Get ride request by UUID
    /// </summary>
    [HttpGet("{requestUuid}")]
    [ProducesResponseType(typeof(RideRequestResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> GetRideRequestByUuid([FromRoute] string requestUuid)
    {
        var response = await _adminRideRequestService.GetRideRequestByUuidAsync(requestUuid);
        return Ok(response);
    }
}
