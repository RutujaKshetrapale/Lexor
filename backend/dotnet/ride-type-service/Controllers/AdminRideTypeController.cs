using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using RideTypeService.DTOs.Request;
using RideTypeService.DTOs.Response;
using RideTypeService.Services;

namespace RideTypeService.Controllers;

[ApiController]
[Route("api/v1/admin/ride-types")]
[Authorize(Roles = "ROLE_ADMIN,ADMIN")]
public class AdminRideTypeController : ControllerBase
{
    private readonly IRideTypeService _rideTypeService;

    public AdminRideTypeController(IRideTypeService rideTypeService)
    {
        _rideTypeService = rideTypeService;
    }

    /// <summary>
    /// Create a new ride category with fare rates
    /// </summary>
    [HttpPost]
    [ProducesResponseType(typeof(RideTypeResponse), StatusCodes.Status201Created)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status409Conflict)]
    public async Task<IActionResult> CreateRideType([FromBody] CreateRideTypeRequest request)
    {
        var response = await _rideTypeService.CreateRideTypeAsync(request);
        return StatusCode(StatusCodes.Status201Created, response);
    }

    /// <summary>
    /// List all ride categories including disabled
    /// </summary>
    [HttpGet]
    [ProducesResponseType(typeof(List<RideTypeResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    public async Task<IActionResult> GetAllRideTypes()
    {
        var response = await _rideTypeService.GetAllRideTypesAdminAsync();
        return Ok(response);
    }

    /// <summary>
    /// Retrieve full administrative details of any category
    /// </summary>
    [HttpGet("{rideTypeUuid}")]
    [ProducesResponseType(typeof(RideTypeResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> GetRideTypeByUuid([FromRoute] string rideTypeUuid)
    {
        var response = await _rideTypeService.GetRideTypeByUuidAdminAsync(rideTypeUuid);
        return Ok(response);
    }

    /// <summary>
    /// Update permitted fields for a ride category
    /// </summary>
    [HttpPut("{rideTypeUuid}")]
    [ProducesResponseType(typeof(RideTypeResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status409Conflict)]
    public async Task<IActionResult> UpdateRideType([FromRoute] string rideTypeUuid, [FromBody] UpdateRideTypeRequest request)
    {
        var response = await _rideTypeService.UpdateRideTypeAsync(rideTypeUuid, request);
        return Ok(response);
    }

    /// <summary>
    /// Enable or disable a ride category
    /// </summary>
    [HttpPatch("{rideTypeUuid}/status")]
    [ProducesResponseType(typeof(RideTypeResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> UpdateRideTypeStatus([FromRoute] string rideTypeUuid, [FromBody] UpdateRideTypeStatusRequest request)
    {
        var response = await _rideTypeService.UpdateRideTypeStatusAsync(rideTypeUuid, request);
        return Ok(response);
    }

    /// <summary>
    /// Safely delete a ride category (rejects hard deletion to preserve historical data)
    /// </summary>
    [HttpDelete("{rideTypeUuid}")]
    [ProducesResponseType(StatusCodes.Status204NoContent)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> DeleteRideType([FromRoute] string rideTypeUuid)
    {
        await _rideTypeService.DeleteRideTypeSafelyAsync(rideTypeUuid);
        return NoContent();
    }
}
