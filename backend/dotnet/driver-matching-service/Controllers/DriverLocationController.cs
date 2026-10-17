using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using DriverMatchingService.DTOs.Request;
using DriverMatchingService.DTOs.Response;
using DriverMatchingService.Exceptions;
using DriverMatchingService.Services;

namespace DriverMatchingService.Controllers;

[ApiController]
[Route("api/v1/drivers/me")]
[Authorize(Roles = "DRIVER,ROLE_DRIVER")]
public class DriverLocationController : ControllerBase
{
    private readonly IDriverLocationService _locationService;

    public DriverLocationController(IDriverLocationService locationService)
    {
        _locationService = locationService;
    }

    [HttpPut("location")]
    [ProducesResponseType(typeof(LocationUpdateResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<ActionResult<LocationUpdateResponse>> UpdateLocation([FromBody] LocationUpdateRequestDto request)
    {
        var driverUuid = User.FindFirstValue(ClaimTypes.NameIdentifier)
            ?? User.FindFirstValue("sub");

        if (string.IsNullOrWhiteSpace(driverUuid))
        {
            throw new UnauthorizedException("Unable to determine driver identity from authenticated claims");
        }

        var result = await _locationService.UpdateDriverLocationAsync(driverUuid, request);
        return Ok(result);
    }
}
