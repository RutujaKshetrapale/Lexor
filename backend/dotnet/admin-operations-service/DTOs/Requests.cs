using System.ComponentModel.DataAnnotations;

namespace AdminOperationsService.DTOs;

public class RecordAuditLogRequest
{
    [Required]
    public string Action { get; set; } = string.Empty;

    [Required]
    public string ResourceType { get; set; } = string.Empty;

    public string? ResourceUuid { get; set; }
    public string? Reason { get; set; }
    public string? Details { get; set; }
}

public class UpdateSettingRequest
{
    [Required]
    public string SettingKey { get; set; } = string.Empty;

    [Required]
    public string SettingValue { get; set; } = string.Empty;

    public string? Description { get; set; }
}

public class EmergencyCancelRideRequest
{
    [Required]
    public string BookingUuid { get; set; } = string.Empty;

    [Required]
    public string Reason { get; set; } = string.Empty;
}
