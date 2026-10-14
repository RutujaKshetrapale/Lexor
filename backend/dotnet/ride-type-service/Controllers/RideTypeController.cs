using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using RideTypeService.DTOs.Response;
using RideTypeService.Services;

namespace RideTypeService.Controllers;

[ApiController]
[Route("api/v1/ride-types")]
[Authorize]
public class RideTypeController : ControllerBase
{
    private readonly IRideTypeService _rideTypeService;

    public RideTypeController(IRideTypeService rideTypeService)
    {
        _rideTypeService = rideTypeService;
    }

    /// <summary>
    /// List all enabled ride categories available for booking
    /// </summary>
    [HttpGet]
    [ProducesResponseType(typeof(List<RideTypeResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    public async Task<IActionResult> GetAvailableRideTypes()
    {
        var response = await _rideTypeService.GetEnabledRideTypesAsync();
        return Ok(response);
    }

    /// <summary>
    /// Retrieve single enabled ride type details by UUID
    /// </summary>
    [HttpGet("{rideTypeUuid}")]
    [ProducesResponseType(typeof(RideTypeResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> GetRideTypeByUuid([FromRoute] string rideTypeUuid)
    {
        var response = await _rideTypeService.GetEnabledRideTypeByUuidAsync(rideTypeUuid);
        return Ok(response);
    }
}
