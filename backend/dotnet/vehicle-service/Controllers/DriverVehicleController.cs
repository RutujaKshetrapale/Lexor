using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using VehicleService.DTOs.Request;
using VehicleService.DTOs.Response;
using VehicleService.Services;

namespace VehicleService.Controllers;

[ApiController]
[Route("api/v1/vehicles")]
[Authorize(Roles = "ROLE_DRIVER,DRIVER")]
public class DriverVehicleController : ControllerBase
{
    private readonly IVehicleService _vehicleService;

    public DriverVehicleController(IVehicleService vehicleService)
    {
        _vehicleService = vehicleService;
    }

    [HttpPost]
    [ProducesResponseType(typeof(VehicleResponse), StatusCodes.Status201Created)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status409Conflict)]
    public async Task<IActionResult> RegisterVehicle([FromBody] RegisterVehicleRequest request)
    {
        var response = await _vehicleService.RegisterVehicleAsync(User, request);
        return StatusCode(StatusCodes.Status201Created, response);
    }

    [HttpGet]
    [ProducesResponseType(typeof(List<VehicleResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    public async Task<IActionResult> GetMyVehicles()
    {
        var response = await _vehicleService.GetMyVehiclesAsync(User);
        return Ok(response);
    }

    [HttpGet("{vehicleUuid}")]
    [ProducesResponseType(typeof(VehicleResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> GetVehicleByUuid([FromRoute] string vehicleUuid)
    {
        var response = await _vehicleService.GetVehicleByUuidAsync(User, vehicleUuid);
        return Ok(response);
    }

    [HttpPut("{vehicleUuid}")]
    [ProducesResponseType(typeof(VehicleResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> UpdateVehicle(
        [FromRoute] string vehicleUuid,
        [FromBody] UpdateVehicleRequest request)
    {
        var response = await _vehicleService.UpdateVehicleAsync(User, vehicleUuid, request);
        return Ok(response);
    }

    [HttpPatch("{vehicleUuid}/status")]
    [ProducesResponseType(typeof(VehicleResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> UpdateVehicleStatus(
        [FromRoute] string vehicleUuid,
        [FromBody] VehicleStatusUpdateRequest request)
    {
        var response = await _vehicleService.UpdateVehicleStatusAsync(User, vehicleUuid, request.IsActive!.Value);
        return Ok(response);
    }

    [HttpPost("{vehicleUuid}/submit-verification")]
    [ProducesResponseType(typeof(VehicleResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> SubmitForVerification([FromRoute] string vehicleUuid)
    {
        var response = await _vehicleService.SubmitForVerificationAsync(User, vehicleUuid);
        return Ok(response);
    }

    [HttpGet("{vehicleUuid}/verification-status")]
    [ProducesResponseType(typeof(VehicleVerificationStatusResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> GetVerificationStatus([FromRoute] string vehicleUuid)
    {
        var response = await _vehicleService.GetVerificationStatusAsync(User, vehicleUuid);
        return Ok(response);
    }

    [HttpGet("{vehicleUuid}/verification-history")]
    [ProducesResponseType(typeof(List<VehicleVerificationHistoryResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> GetVerificationHistory([FromRoute] string vehicleUuid)
    {
        var response = await _vehicleService.GetVehicleVerificationHistoryAsync(User, vehicleUuid);
        return Ok(response);
    }

    [HttpGet("{vehicleUuid}/assignments")]
    [ProducesResponseType(typeof(List<DriverVehicleAssignmentResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> GetVehicleAssignments([FromRoute] string vehicleUuid)
    {
        var response = await _vehicleService.GetVehicleAssignmentsAsync(User, vehicleUuid);
        return Ok(response);
    }
}
