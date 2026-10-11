using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using DriverService.DTOs.Request;
using DriverService.DTOs.Response;
using DriverService.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace DriverService.Controllers;

[ApiController]
[Route("api/v1/drivers/me")]
[Authorize(Roles = "ROLE_DRIVER,DRIVER")]
[Produces("application/json")]
public class DriverProfileController : ControllerBase
{
    private readonly IDriverProfileService _driverProfileService;

    public DriverProfileController(IDriverProfileService driverProfileService)
    {
        _driverProfileService = driverProfileService;
    }

    [HttpGet]
    [ProducesResponseType(typeof(DriverProfileResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<ActionResult<DriverProfileResponse>> GetCurrentDriverProfile()
    {
        var userUuid = GetCurrentUserUuid();
        var response = await _driverProfileService.GetDriverProfileAsync(userUuid);
        return Ok(response);
    }

    [HttpPut]
    [ProducesResponseType(typeof(DriverProfileResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status409Conflict)]
    public async Task<ActionResult<DriverProfileResponse>> UpdateDriverProfile([FromBody] UpdateDriverProfileRequest request)
    {
        if (!ModelState.IsValid)
        {
            return BadRequest(ModelState);
        }

        var userUuid = GetCurrentUserUuid();
        var response = await _driverProfileService.UpdateDriverProfileAsync(userUuid, request);
        return Ok(response);
    }

    [HttpPatch]
    [ProducesResponseType(typeof(DriverProfileResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status409Conflict)]
    public async Task<ActionResult<DriverProfileResponse>> PatchDriverProfile([FromBody] PatchDriverProfileRequest request)
    {
        if (!ModelState.IsValid)
        {
            return BadRequest(ModelState);
        }

        var userUuid = GetCurrentUserUuid();
        var response = await _driverProfileService.PatchDriverProfileAsync(userUuid, request);
        return Ok(response);
    }

    [HttpGet("onboarding")]
    [ProducesResponseType(typeof(DriverOnboardingResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    public async Task<ActionResult<DriverOnboardingResponse>> GetOnboardingStatus()
    {
        var userUuid = GetCurrentUserUuid();
        var response = await _driverProfileService.GetOnboardingStatusAsync(userUuid);
        return Ok(response);
    }

    [HttpPost("submit-for-review")]
    [ProducesResponseType(typeof(DriverOnboardingResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    public async Task<ActionResult<DriverOnboardingResponse>> SubmitForReview()
    {
        var userUuid = GetCurrentUserUuid();
        var response = await _driverProfileService.SubmitForReviewAsync(userUuid);
        return Ok(response);
    }

    [HttpPatch("availability")]
    [ProducesResponseType(typeof(DriverAvailabilityResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    public async Task<ActionResult<DriverAvailabilityResponse>> UpdateAvailability([FromBody] DriverAvailabilityRequest request)
    {
        var userUuid = GetCurrentUserUuid();
        var response = await _driverProfileService.UpdateAvailabilityAsync(userUuid, request);
        return Ok(response);
    }

    private string GetCurrentUserUuid()
    {
        var uuid = User.FindFirstValue(ClaimTypes.NameIdentifier)
                   ?? User.FindFirstValue(JwtRegisteredClaimNames.Sub)
                   ?? User.FindFirstValue("sub")
                   ?? User.FindFirstValue("uuid");

        if (string.IsNullOrEmpty(uuid))
        {
            throw new UnauthorizedAccessException("User identity claim not found in JWT token");
        }
        return uuid;
    }
}
