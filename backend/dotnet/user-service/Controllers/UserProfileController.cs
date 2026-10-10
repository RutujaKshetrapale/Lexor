using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using UserService.DTOs.Request;
using UserService.DTOs.Response;
using UserService.Services;

namespace UserService.Controllers;

[ApiController]
[Route("api/v1/users/me")]
[Authorize]
public class UserProfileController : ControllerBase
{
    private readonly IUserService _userService;

    public UserProfileController(IUserService userService)
    {
        _userService = userService;
    }

    [HttpGet]
    [ProducesResponseType(typeof(UserResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<ActionResult<UserResponse>> GetCurrentUserProfile(CancellationToken cancellationToken)
    {
        var currentUserUuid = GetCurrentUserUuid();
        var response = await _userService.GetCurrentUserProfileAsync(currentUserUuid, cancellationToken);
        return Ok(response);
    }

    [HttpPut]
    [ProducesResponseType(typeof(UserResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status409Conflict)]
    public async Task<ActionResult<UserResponse>> UpdateCurrentUserProfile(
        [FromBody] UpdateProfileRequest request,
        CancellationToken cancellationToken)
    {
        var currentUserUuid = GetCurrentUserUuid();
        var response = await _userService.UpdateCurrentUserProfileAsync(currentUserUuid, request, cancellationToken);
        return Ok(response);
    }

    [HttpPatch]
    [ProducesResponseType(typeof(UserResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status409Conflict)]
    public async Task<ActionResult<UserResponse>> PatchCurrentUserProfile(
        [FromBody] PatchProfileRequest request,
        CancellationToken cancellationToken)
    {
        var currentUserUuid = GetCurrentUserUuid();
        var response = await _userService.PatchCurrentUserProfileAsync(currentUserUuid, request, cancellationToken);
        return Ok(response);
    }

    [HttpDelete]
    [ProducesResponseType(StatusCodes.Status204NoContent)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    public async Task<IActionResult> DeactivateCurrentUser(CancellationToken cancellationToken)
    {
        var currentUserUuid = GetCurrentUserUuid();
        await _userService.DeactivateCurrentUserAsync(currentUserUuid, cancellationToken);
        return NoContent();
    }

    private string GetCurrentUserUuid()
    {
        var uuid = User.FindFirstValue(ClaimTypes.NameIdentifier)
                ?? User.FindFirstValue(JwtRegisteredClaimNames.Sub)
                ?? User.FindFirstValue("sub")
                ?? User.FindFirstValue("uuid");

        if (string.IsNullOrEmpty(uuid))
        {
            throw new UnauthorizedAccessException("Could not resolve authenticated user identity from token");
        }

        return uuid;
    }
}
