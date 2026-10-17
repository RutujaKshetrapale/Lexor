using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;
using DriverMatchingService.Configuration;
using DriverMatchingService.DTOs.Request;
using DriverMatchingService.DTOs.Response;
using DriverMatchingService.Exceptions;
using DriverMatchingService.Services;

namespace DriverMatchingService.Controllers;

[ApiController]
[Route("api/v1/internal/matching/ride-requests")]
public class InternalMatchingController : ControllerBase
{
    private readonly IDriverMatchingService _matchingService;
    private readonly MatchingSettings _matchingSettings;

    public InternalMatchingController(
        IDriverMatchingService matchingService,
        IOptions<MatchingSettings> matchingSettings)
    {
        _matchingService = matchingService;
        _matchingSettings = matchingSettings.Value;
    }

    private void AuthorizeInternalCaller()
    {
        // 1. Check for valid internal service key header
        if (Request.Headers.TryGetValue("X-Internal-Service-Key", out var headerKey))
        {
            if (headerKey == _matchingSettings.InternalApiKey)
            {
                return; // Authorized via internal service token
            }
        }

        // 2. Check for authenticated user with ADMIN role
        if (User.Identity?.IsAuthenticated == true &&
            (User.IsInRole("ADMIN") || User.IsInRole("ROLE_ADMIN")))
        {
            return; // Authorized via admin JWT
        }

        if (User.Identity?.IsAuthenticated == true)
        {
            throw new ForbiddenException("Access denied: You do not have permission to access this resource");
        }

        throw new UnauthorizedException("Full authentication is required to access this resource");
    }

    [HttpPost("{requestUuid}/attempts")]
    [ProducesResponseType(typeof(MatchingAttemptResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status409Conflict)]
    public async Task<ActionResult<MatchingAttemptResponse>> InitiateMatching(
        [FromRoute] string requestUuid,
        [FromHeader(Name = "X-Correlation-Id")] string? correlationId)
    {
        AuthorizeInternalCaller();

        var result = await _matchingService.InitiateMatchingAsync(requestUuid, correlationId);
        return Ok(result);
    }

    [HttpGet("{requestUuid}")]
    [ProducesResponseType(typeof(MatchingAttemptResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<ActionResult<MatchingAttemptResponse>> GetMatchingStatus([FromRoute] string requestUuid)
    {
        AuthorizeInternalCaller();

        var result = await _matchingService.GetMatchingStatusAsync(requestUuid);
        return Ok(result);
    }

    [HttpPost("{requestUuid}/retry")]
    [ProducesResponseType(typeof(MatchingAttemptResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status409Conflict)]
    public async Task<ActionResult<MatchingAttemptResponse>> RetryMatching(
        [FromRoute] string requestUuid,
        [FromBody] MatchingRetryRequestDto? retryRequest)
    {
        AuthorizeInternalCaller();

        var result = await _matchingService.RetryMatchingAsync(
            requestUuid,
            retryRequest ?? new MatchingRetryRequestDto());
        return Ok(result);
    }
}
