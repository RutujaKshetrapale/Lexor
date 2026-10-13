using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using VehicleService.DTOs.Request;
using VehicleService.DTOs.Response;
using VehicleService.Enums;
using VehicleService.Services;

namespace VehicleService.Controllers;

[ApiController]
[Route("api/v1/admin/vehicles")]
[Authorize(Roles = "ROLE_ADMIN,ADMIN")]
public class AdminVehicleController : ControllerBase
{
    private readonly IVehicleAdminService _adminService;

    public AdminVehicleController(IVehicleAdminService adminService)
    {
        _adminService = adminService;
    }

    [HttpGet]
    [ProducesResponseType(typeof(PagedResponse<VehicleResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    public async Task<IActionResult> ListVehicles(
        [FromQuery] VehicleVerificationStatus? verificationStatus,
        [FromQuery] bool? isActive,
        [FromQuery] string? rideTypeCategory,
        [FromQuery] string? search,
        [FromQuery] int page = 0,
        [FromQuery] int size = 10,
        [FromQuery] string sortBy = "createdAt",
        [FromQuery] string sortDir = "desc")
    {
        var response = await _adminService.ListVehiclesAsync(
            verificationStatus, isActive, rideTypeCategory, search, page, size, sortBy, sortDir
        );
        return Ok(response);
    }

    [HttpGet("pending")]
    [ProducesResponseType(typeof(PagedResponse<VehicleResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    public async Task<IActionResult> ListPendingVehicles(
        [FromQuery] int page = 0,
        [FromQuery] int size = 10,
        [FromQuery] string sortBy = "createdAt",
        [FromQuery] string sortDir = "asc")
    {
        var response = await _adminService.ListPendingVehiclesAsync(page, size, sortBy, sortDir);
        return Ok(response);
    }

    [HttpGet("{vehicleUuid}")]
    [ProducesResponseType(typeof(VehicleResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> GetVehicleDetails([FromRoute] string vehicleUuid)
    {
        var response = await _adminService.GetVehicleDetailsAsync(vehicleUuid);
        return Ok(response);
    }

    [HttpGet("{vehicleUuid}/documents")]
    [ProducesResponseType(typeof(List<VehicleDocumentResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> GetVehicleDocuments([FromRoute] string vehicleUuid)
    {
        var response = await _adminService.GetVehicleDocumentsAsync(vehicleUuid);
        return Ok(response);
    }

    [HttpGet("{vehicleUuid}/documents/{documentId}/download")]
    [ProducesResponseType(StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> DownloadDocument(
        [FromRoute] string vehicleUuid,
        [FromRoute] long documentId)
    {
        var (stream, contentType, fileName) = await _adminService.DownloadDocumentAsync(vehicleUuid, documentId);
        return File(stream, contentType, fileName);
    }

    [HttpPatch("{vehicleUuid}/documents/{documentId}/review")]
    [ProducesResponseType(typeof(VehicleDocumentResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> ReviewDocument(
        [FromRoute] string vehicleUuid,
        [FromRoute] long documentId,
        [FromBody] ReviewVehicleDocumentRequest request)
    {
        var response = await _adminService.ReviewDocumentAsync(User, vehicleUuid, documentId, request);
        return Ok(response);
    }

    [HttpPatch("{vehicleUuid}/verification")]
    [ProducesResponseType(typeof(VehicleResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> ReviewVehicle(
        [FromRoute] string vehicleUuid,
        [FromBody] ReviewVehicleRequest request)
    {
        var response = await _adminService.ReviewVehicleAsync(User, vehicleUuid, request);
        return Ok(response);
    }

    [HttpPatch("{vehicleUuid}/suspension")]
    [ProducesResponseType(typeof(VehicleResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> SuspendOrReinstateVehicle(
        [FromRoute] string vehicleUuid,
        [FromBody] SuspendVehicleRequest request)
    {
        var response = await _adminService.SuspendOrReinstateVehicleAsync(User, vehicleUuid, request);
        return Ok(response);
    }

    [HttpGet("{vehicleUuid}/verification-history")]
    [ProducesResponseType(typeof(List<VehicleVerificationHistoryResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> GetVerificationHistory([FromRoute] string vehicleUuid)
    {
        var response = await _adminService.GetVerificationHistoryAsync(vehicleUuid);
        return Ok(response);
    }

    [HttpPost("{vehicleUuid}/assignments")]
    [ProducesResponseType(typeof(DriverVehicleAssignmentResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> AssignDriver(
        [FromRoute] string vehicleUuid,
        [FromBody] AssignDriverRequest request)
    {
        var response = await _adminService.AssignDriverAsync(User, vehicleUuid, request);
        return Ok(response);
    }

    [HttpDelete("{vehicleUuid}/assignments/{assignmentId}")]
    [ProducesResponseType(typeof(DriverVehicleAssignmentResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> UnassignDriver(
        [FromRoute] string vehicleUuid,
        [FromRoute] long assignmentId,
        [FromQuery] string? reason)
    {
        var response = await _adminService.UnassignDriverAsync(User, vehicleUuid, assignmentId, reason);
        return Ok(response);
    }

    [HttpGet("{vehicleUuid}/assignments")]
    [ProducesResponseType(typeof(List<DriverVehicleAssignmentResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> GetAssignmentHistory([FromRoute] string vehicleUuid)
    {
        var response = await _adminService.GetAssignmentHistoryAsync(vehicleUuid);
        return Ok(response);
    }
}
