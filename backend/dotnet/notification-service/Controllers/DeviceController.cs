using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using NotificationService.DTOs;
using NotificationService.Services;

namespace NotificationService.Controllers;

[ApiController]
[Route("api/v1/notifications/devices")]
[Authorize]
public class DeviceController : ControllerBase
{
    private readonly IDeviceService _deviceService;

    public DeviceController(IDeviceService deviceService)
    {
        _deviceService = deviceService;
    }

    private string CurrentUserId =>
        User.FindFirstValue(ClaimTypes.NameIdentifier) ??
        User.FindFirstValue("sub") ??
        throw new UnauthorizedAccessException("Missing user identifier in JWT");

    [HttpPost]
    public async Task<ActionResult<ApiResponse<DeviceDto>>> RegisterDevice([FromBody] DeviceRegistrationRequest request)
    {
        var result = await _deviceService.RegisterDeviceAsync(CurrentUserId, request);
        return StatusCode(StatusCodes.Status201Created, ApiResponse<DeviceDto>.Ok(result, "Device registered successfully"));
    }

    [HttpGet]
    public async Task<ActionResult<ApiResponse<List<DeviceDto>>>> ListDevices()
    {
        var result = await _deviceService.ListUserDevicesAsync(CurrentUserId);
        return Ok(ApiResponse<List<DeviceDto>>.Ok(result));
    }

    [HttpDelete("{deviceUuid}")]
    public async Task<ActionResult<ApiResponse<string>>> DeactivateDevice(string deviceUuid)
    {
        await _deviceService.DeactivateDeviceAsync(CurrentUserId, deviceUuid);
        return Ok(ApiResponse<string>.Ok(deviceUuid, "Device deactivated successfully"));
    }
}
