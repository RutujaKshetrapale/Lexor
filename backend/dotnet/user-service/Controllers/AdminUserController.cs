using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using UserService.DTOs.Request;
using UserService.DTOs.Response;
using UserService.Entities;
using UserService.Services;

namespace UserService.Controllers;

[ApiController]
[Route("api/v1/users")]
[Authorize(Roles = "ROLE_ADMIN,ADMIN")]
public class AdminUserController : ControllerBase
{
    private readonly IUserService _userService;

    public AdminUserController(IUserService userService)
    {
        _userService = userService;
    }

    [HttpPost]
    [ProducesResponseType(typeof(UserResponse), StatusCodes.Status201Created)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status409Conflict)]
    public async Task<ActionResult<UserResponse>> CreateUser(
        [FromBody] AdminCreateUserRequest request,
        CancellationToken cancellationToken)
    {
        var response = await _userService.CreateUserAsync(request, cancellationToken);
        return CreatedAtAction(nameof(GetUserByUuid), new { uuid = response.Uuid }, response);
    }

    [HttpGet]
    [ProducesResponseType(typeof(PagedResponse<UserResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    public async Task<ActionResult<PagedResponse<UserResponse>>> GetUsers(
        [FromQuery] UserRole? role,
        [FromQuery] UserStatus? status,
        [FromQuery] string? search,
        [FromQuery] int page = 0,
        [FromQuery] int size = 20,
        [FromQuery] string sortBy = "createdAt",
        [FromQuery] string sortDir = "desc",
        CancellationToken cancellationToken = default)
    {
        var response = await _userService.GetUsersAsync(role, status, search, page, size, sortBy, sortDir, cancellationToken);
        return Ok(response);
    }

    [HttpGet("{uuid}")]
    [ProducesResponseType(typeof(UserResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<ActionResult<UserResponse>> GetUserByUuid(
        [FromRoute] string uuid,
        CancellationToken cancellationToken)
    {
        var response = await _userService.GetUserByUuidAsync(uuid, cancellationToken);
        return Ok(response);
    }

    [HttpPut("{uuid}")]
    [ProducesResponseType(typeof(UserResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status409Conflict)]
    public async Task<ActionResult<UserResponse>> UpdateUserByUuid(
        [FromRoute] string uuid,
        [FromBody] AdminUpdateUserRequest request,
        CancellationToken cancellationToken)
    {
        var response = await _userService.UpdateUserByUuidAsync(uuid, request, cancellationToken);
        return Ok(response);
    }

    [HttpPatch("{uuid}")]
    [ProducesResponseType(typeof(UserResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status409Conflict)]
    public async Task<ActionResult<UserResponse>> PatchUserByUuid(
        [FromRoute] string uuid,
        [FromBody] AdminPatchUserRequest request,
        CancellationToken cancellationToken)
    {
        var response = await _userService.PatchUserByUuidAsync(uuid, request, cancellationToken);
        return Ok(response);
    }

    [HttpPatch("{uuid}/status")]
    [ProducesResponseType(typeof(UserResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<ActionResult<UserResponse>> UpdateUserStatus(
        [FromRoute] string uuid,
        [FromBody] UpdateUserStatusRequest request,
        CancellationToken cancellationToken)
    {
        var adminUuid = GetCurrentUserUuid();
        var response = await _userService.UpdateUserStatusAsync(adminUuid, uuid, request, cancellationToken);
        return Ok(response);
    }

    [HttpDelete("{uuid}")]
    [ProducesResponseType(StatusCodes.Status204NoContent)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> DeactivateUserByUuid(
        [FromRoute] string uuid,
        CancellationToken cancellationToken)
    {
        var adminUuid = GetCurrentUserUuid();
        await _userService.DeactivateUserByUuidAsync(adminUuid, uuid, cancellationToken);
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
