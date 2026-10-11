using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using DriverService.DTOs.Request;
using DriverService.DTOs.Response;
using DriverService.Enums;
using DriverService.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace DriverService.Controllers;

[ApiController]
[Route("api/v1/admin")]
[Authorize(Roles = "ROLE_ADMIN,ADMIN")]
public class AdminDriverController : ControllerBase
{
    private readonly IDriverVerificationService _verificationService;

    public AdminDriverController(IDriverVerificationService verificationService)
    {
        _verificationService = verificationService;
    }

    [HttpGet("drivers")]
    [Produces("application/json")]
    [ProducesResponseType(typeof(PagedResponse<DriverProfileResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    public async Task<ActionResult<PagedResponse<DriverProfileResponse>>> ListDrivers(
        [FromQuery] OnboardingStatus? verificationStatus,
        [FromQuery] UserStatus? accountStatus,
        [FromQuery] DriverStatus? availabilityStatus,
        [FromQuery] string? search,
        [FromQuery] int page = 0,
        [FromQuery] int size = 10,
        [FromQuery] string sortBy = "createdAt",
        [FromQuery] string sortDir = "desc")
    {
        var response = await _verificationService.ListDriversAsync(
            verificationStatus, accountStatus, availabilityStatus, search, page, size, sortBy, sortDir
        );
        return Ok(response);
    }

    [HttpGet("drivers/{driverUuid}")]
    [Produces("application/json")]
    [ProducesResponseType(typeof(DriverProfileResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<ActionResult<DriverProfileResponse>> GetDriverByUuid(string driverUuid)
    {
        var response = await _verificationService.GetDriverByUuidAsync(driverUuid);
        return Ok(response);
    }

    [HttpGet("drivers/{driverUuid}/documents")]
    [Produces("application/json")]
    [ProducesResponseType(typeof(List<DocumentResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<ActionResult<List<DocumentResponse>>> GetDriverDocumentsByUuid(string driverUuid)
    {
        var response = await _verificationService.GetDriverDocumentsByUuidAsync(driverUuid);
        return Ok(response);
    }

    [HttpGet("driver-documents")]
    [Produces("application/json")]
    [ProducesResponseType(typeof(PagedResponse<DocumentResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    public async Task<ActionResult<PagedResponse<DocumentResponse>>> ListAwaitingDocuments(
        [FromQuery] DocumentVerificationStatus? status,
        [FromQuery] DocumentType? documentType,
        [FromQuery] string? driverUuid,
        [FromQuery] int page = 0,
        [FromQuery] int size = 10)
    {
        var response = await _verificationService.ListAwaitingDocumentsAsync(
            status, documentType, driverUuid, page, size
        );
        return Ok(response);
    }

    [HttpGet("driver-documents/{documentId:long}/download")]
    [ProducesResponseType(StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> DownloadDocumentForAdmin(long documentId)
    {
        var (fileStream, contentType, fileName) = await _verificationService.DownloadDocumentForAdminAsync(documentId);
        return File(fileStream, contentType, fileName);
    }

    [HttpPatch("driver-documents/{documentId:long}/status")]
    [Produces("application/json")]
    [ProducesResponseType(typeof(DocumentResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<ActionResult<DocumentResponse>> ReviewDocument(long documentId, [FromBody] ReviewDocumentRequest request)
    {
        if (!ModelState.IsValid)
        {
            return BadRequest(ModelState);
        }

        var adminUuid = GetCurrentAdminUuid();
        var response = await _verificationService.ReviewDocumentAsync(adminUuid, documentId, request);
        return Ok(response);
    }

    [HttpGet("drivers/{driverUuid}/verification-history")]
    [Produces("application/json")]
    [ProducesResponseType(typeof(List<VerificationHistoryResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    public async Task<ActionResult<List<VerificationHistoryResponse>>> GetDriverVerificationHistory(string driverUuid)
    {
        var response = await _verificationService.GetDriverVerificationHistoryAsync(driverUuid);
        return Ok(response);
    }

    [HttpPatch("drivers/{driverUuid}/verification-status")]
    [Produces("application/json")]
    [ProducesResponseType(typeof(DriverProfileResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<ActionResult<DriverProfileResponse>> ReviewDriverVerification(string driverUuid, [FromBody] VerifyDriverRequest request)
    {
        if (!ModelState.IsValid)
        {
            return BadRequest(ModelState);
        }

        var adminUuid = GetCurrentAdminUuid();
        var response = await _verificationService.ReviewDriverVerificationAsync(adminUuid, driverUuid, request);
        return Ok(response);
    }

    [HttpPatch("drivers/{driverUuid}/suspension")]
    [Produces("application/json")]
    [ProducesResponseType(typeof(DriverProfileResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<ActionResult<DriverProfileResponse>> UpdateDriverSuspension(string driverUuid, [FromBody] DriverSuspensionRequest request)
    {
        if (!ModelState.IsValid)
        {
            return BadRequest(ModelState);
        }

        var adminUuid = GetCurrentAdminUuid();
        var response = await _verificationService.UpdateDriverSuspensionAsync(adminUuid, driverUuid, request);
        return Ok(response);
    }

    private string GetCurrentAdminUuid()
    {
        var uuid = User.FindFirstValue(ClaimTypes.NameIdentifier)
                   ?? User.FindFirstValue(JwtRegisteredClaimNames.Sub)
                   ?? User.FindFirstValue("sub")
                   ?? User.FindFirstValue("uuid");

        if (string.IsNullOrEmpty(uuid))
        {
            throw new UnauthorizedAccessException("Admin identity claim not found in JWT token");
        }
        return uuid;
    }
}
