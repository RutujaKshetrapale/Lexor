using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using VehicleService.DTOs.Response;
using VehicleService.Enums;
using VehicleService.Services;

namespace VehicleService.Controllers;

[ApiController]
[Route("api/v1/vehicles/{vehicleUuid}/documents")]
[Authorize(Roles = "ROLE_DRIVER,DRIVER")]
public class VehicleDocumentController : ControllerBase
{
    private readonly IVehicleDocumentService _documentService;

    public VehicleDocumentController(IVehicleDocumentService documentService)
    {
        _documentService = documentService;
    }

    [HttpPost]
    [Consumes("multipart/form-data")]
    [ProducesResponseType(typeof(VehicleDocumentResponse), StatusCodes.Status201Created)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status413PayloadTooLarge)]
    public async Task<IActionResult> UploadDocument(
        [FromRoute] string vehicleUuid,
        [FromForm] VehicleDocumentType documentType,
        [FromForm] string? documentNumber,
        [FromForm] DateOnly? issueDate,
        [FromForm] DateOnly? expiryDate,
        [FromForm] string? issuingAuthority,
        IFormFile file)
    {
        var response = await _documentService.UploadDocumentAsync(
            User, vehicleUuid, documentType, documentNumber, issueDate, expiryDate, issuingAuthority, file
        );
        return StatusCode(StatusCodes.Status201Created, response);
    }

    [HttpGet]
    [ProducesResponseType(typeof(List<VehicleDocumentResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    public async Task<IActionResult> GetVehicleDocuments([FromRoute] string vehicleUuid)
    {
        var response = await _documentService.GetVehicleDocumentsAsync(User, vehicleUuid);
        return Ok(response);
    }

    [HttpGet("{documentId}")]
    [ProducesResponseType(typeof(VehicleDocumentResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> GetDocumentById(
        [FromRoute] string vehicleUuid,
        [FromRoute] long documentId)
    {
        var response = await _documentService.GetDocumentByIdAsync(User, vehicleUuid, documentId);
        return Ok(response);
    }

    [HttpGet("{documentId}/download")]
    [ProducesResponseType(StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> DownloadDocument(
        [FromRoute] string vehicleUuid,
        [FromRoute] long documentId)
    {
        var (stream, contentType, fileName) = await _documentService.DownloadDocumentAsync(User, vehicleUuid, documentId);
        return File(stream, contentType, fileName);
    }

    [HttpPost("{documentId}/resubmit")]
    [Consumes("multipart/form-data")]
    [ProducesResponseType(typeof(VehicleDocumentResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> ResubmitDocument(
        [FromRoute] string vehicleUuid,
        [FromRoute] long documentId,
        [FromForm] string? documentNumber,
        [FromForm] DateOnly? issueDate,
        [FromForm] DateOnly? expiryDate,
        [FromForm] string? issuingAuthority,
        IFormFile file)
    {
        var response = await _documentService.ResubmitDocumentAsync(
            User, vehicleUuid, documentId, documentNumber, issueDate, expiryDate, issuingAuthority, file
        );
        return Ok(response);
    }

    [HttpDelete("{documentId}")]
    [ProducesResponseType(StatusCodes.Status204NoContent)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> DeleteDocument(
        [FromRoute] string vehicleUuid,
        [FromRoute] long documentId)
    {
        await _documentService.DeleteDocumentAsync(User, vehicleUuid, documentId);
        return NoContent();
    }
}
