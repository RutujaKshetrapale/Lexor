using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using RideRequestService.DTOs.Request;
using RideRequestService.DTOs.Response;
using RideRequestService.Enums;
using RideRequestService.Services;

namespace RideRequestService.Controllers;

[ApiController]
[Route("api/v1/ride-requests")]
[Authorize(Roles = "RIDER,ROLE_RIDER")]
public class RideRequestController : ControllerBase
{
    private readonly IRideRequestService _rideRequestService;

    public RideRequestController(IRideRequestService rideRequestService)
    {
        _rideRequestService = rideRequestService;
    }

    /// <summary>
    /// Create ride request
    /// </summary>
    [HttpPost]
    [ProducesResponseType(typeof(RideRequestResponse), StatusCodes.Status201Created)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> CreateRideRequest(
        [FromBody] CreateRideRequestDto request,
        [FromHeader(Name = "Authorization")] string? authorization)
    {
        var response = await _rideRequestService.CreateRideRequestAsync(User, request, authorization);
        return StatusCode(StatusCodes.Status201Created, response);
    }

    /// <summary>
    /// List authenticated rider's requests
    /// </summary>
    [HttpGet("me")]
    [ProducesResponseType(typeof(PageResponse<RideRequestResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    public async Task<IActionResult> GetMyRideRequests(
        [FromQuery] RideRequestStatus? status,
        [FromQuery] int page = 0,
        [FromQuery] int size = 10)
    {
        var response = await _rideRequestService.GetMyRideRequestsAsync(User, page, size, status);
        return Ok(response);
    }

    /// <summary>
    /// Get rider's request details by UUID
    /// </summary>
    [HttpGet("me/{requestUuid}")]
    [ProducesResponseType(typeof(RideRequestResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> GetMyRideRequestByUuid([FromRoute] string requestUuid)
    {
        var response = await _rideRequestService.GetMyRideRequestByUuidAsync(User, requestUuid);
        return Ok(response);
    }

    /// <summary>
    /// Cancel authenticated rider's ride request
    /// </summary>
    [HttpPatch("{requestUuid}/cancel")]
    [ProducesResponseType(typeof(RideRequestResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status409Conflict)]
    public async Task<IActionResult> CancelRideRequest(
        [FromRoute] string requestUuid,
        [FromBody] CancelRideRequestDto? request = null)
    {
        var response = await _rideRequestService.CancelRideRequestAsync(User, requestUuid, request);
        return Ok(response);
    }
}
