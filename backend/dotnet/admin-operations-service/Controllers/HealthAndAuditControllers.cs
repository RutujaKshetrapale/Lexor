using AdminOperationsService.DTOs;
using AdminOperationsService.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace AdminOperationsService.Controllers;

[ApiController]
[Route("api/v1/admin/health-monitor")]
[Authorize(Roles = "ROLE_ADMIN,ADMIN")]
public class AdminHealthMonitorController : ControllerBase
{
    private readonly IServiceHealthMonitorService _healthMonitorService;

    public AdminHealthMonitorController(IServiceHealthMonitorService healthMonitorService)
    {
        _healthMonitorService = healthMonitorService;
    }

    [HttpGet("services")]
    public async Task<ActionResult<ApiResponse<ServiceHealthStatusResponse>>> GetSystemHealth()
    {
        var result = await _healthMonitorService.GetSystemHealthAsync();
        return Ok(ApiResponse<ServiceHealthStatusResponse>.Ok(result));
    }
}

[ApiController]
[Route("api/v1/admin/audit-logs")]
[Authorize(Roles = "ROLE_ADMIN,ADMIN")]
public class AdminAuditLogController : ControllerBase
{
    private readonly IAdminAuditService _auditService;

    public AdminAuditLogController(IAdminAuditService auditService)
    {
        _auditService = auditService;
    }

    [HttpGet]
    public async Task<ActionResult<ApiResponse<PageResponse<AdminAuditLogResponse>>>> ListAuditLogs(
        [FromQuery] string? adminUserUuid = null,
        [FromQuery] string? action = null,
        [FromQuery] string? resourceType = null,
        [FromQuery] DateTime? startDate = null,
        [FromQuery] DateTime? endDate = null,
        [FromQuery] int page = 0,
        [FromQuery] int size = 20)
    {
        var result = await _auditService.GetAuditLogsAsync(adminUserUuid, action, resourceType, startDate, endDate, page, size);
        return Ok(ApiResponse<PageResponse<AdminAuditLogResponse>>.Ok(result));
    }

    [HttpPost]
    public async Task<ActionResult<ApiResponse<AdminAuditLogResponse>>> RecordAudit([FromBody] RecordAuditLogRequest request)
    {
        var ip = HttpContext.Connection.RemoteIpAddress?.ToString();
        var result = await _auditService.RecordAuditLogAsync(User, request, ip);
        return Ok(ApiResponse<AdminAuditLogResponse>.Ok(result, "Audit log recorded successfully"));
    }

    [HttpGet("settings")]
    public async Task<ActionResult<ApiResponse<List<AdminSettingResponse>>>> ListSettings()
    {
        var result = await _auditService.GetAllSettingsAsync();
        return Ok(ApiResponse<List<AdminSettingResponse>>.Ok(result));
    }

    [HttpPut("settings")]
    public async Task<ActionResult<ApiResponse<AdminSettingResponse>>> UpdateSetting([FromBody] UpdateSettingRequest request)
    {
        var result = await _auditService.UpdateSettingAsync(User, request);
        return Ok(ApiResponse<AdminSettingResponse>.Ok(result, "Setting updated successfully"));
    }
}
