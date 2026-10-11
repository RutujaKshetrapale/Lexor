using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using DriverService.DTOs.Response;
using DriverService.Enums;
using DriverService.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace DriverService.Controllers;

[ApiController]
[Route("api/v1/drivers/me/documents")]
[Authorize(Roles = "ROLE_DRIVER,DRIVER")]
public class DriverDocumentController : ControllerBase
{
    private readonly IDriverDocumentService _driverDocumentService;

    public DriverDocumentController(IDriverDocumentService driverDocumentService)
    {
        _driverDocumentService = driverDocumentService;
    }

    [HttpPost]
    [Consumes("multipart/form-data")]
    [Produces("application/json")]
    [ProducesResponseType(typeof(DocumentResponse), StatusCodes.Status201Created)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status413PayloadTooLarge)]
    public async Task<ActionResult<DocumentResponse>> UploadDocument(
        [FromForm] DocumentType documentType,
        [FromForm] string? documentNumber,
        [FromForm] DateOnly? issueDate,
        [FromForm] DateOnly? expiryDate,
        [FromForm] string? issuingAuthority,
        IFormFile file)
    {
        var userUuid = GetCurrentUserUuid();
        var response = await _driverDocumentService.UploadDocumentAsync(
            userUuid, documentType, documentNumber, issueDate, expiryDate, issuingAuthority, file
        );
        return StatusCode(StatusCodes.Status201Created, response);
    }

    [HttpGet]
    [Produces("application/json")]
    [ProducesResponseType(typeof(List<DocumentResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    public async Task<ActionResult<List<DocumentResponse>>> GetDriverDocuments()
    {
        var userUuid = GetCurrentUserUuid();
        var response = await _driverDocumentService.GetDriverDocumentsAsync(userUuid);
        return Ok(response);
    }

    [HttpGet("{documentId:long}")]
    [Produces("application/json")]
    [ProducesResponseType(typeof(DocumentResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<ActionResult<DocumentResponse>> GetDriverDocumentById(long documentId)
    {
        var userUuid = GetCurrentUserUuid();
        var response = await _driverDocumentService.GetDriverDocumentByIdAsync(userUuid, documentId);
        return Ok(response);
    }

    [HttpGet("{documentId:long}/download")]
    [ProducesResponseType(StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> DownloadDocument(long documentId)
    {
        var userUuid = GetCurrentUserUuid();
        var (fileStream, contentType, fileName) = await _driverDocumentService.DownloadDocumentAsync(userUuid, documentId);
        return File(fileStream, contentType, fileName);
    }

    [HttpPost("{documentId:long}/resubmit")]
    [Consumes("multipart/form-data")]
    [Produces("application/json")]
    [ProducesResponseType(typeof(DocumentResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<ActionResult<DocumentResponse>> ResubmitDocument(
        long documentId,
        IFormFile file,
        [FromForm] string? documentNumber,
        [FromForm] DateOnly? issueDate,
        [FromForm] DateOnly? expiryDate,
        [FromForm] string? issuingAuthority)
    {
        var userUuid = GetCurrentUserUuid();
        var response = await _driverDocumentService.ResubmitDocumentAsync(
            userUuid, documentId, file, documentNumber, issueDate, expiryDate, issuingAuthority
        );
        return Ok(response);
    }

    [HttpDelete("{documentId:long}")]
    [ProducesResponseType(StatusCodes.Status204NoContent)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> DeleteDocument(long documentId)
    {
        var userUuid = GetCurrentUserUuid();
        await _driverDocumentService.DeleteDocumentAsync(userUuid, documentId);
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
            throw new UnauthorizedAccessException("User identity claim not found in JWT token");
        }
        return uuid;
    }
}
