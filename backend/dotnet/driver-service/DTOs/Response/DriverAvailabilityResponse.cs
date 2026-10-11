using DriverService.Enums;

namespace DriverService.DTOs.Response;

public class DriverAvailabilityResponse
{
    public string? DriverUuid { get; set; }
    public DriverStatus Status { get; set; }
    public bool IsOnline { get; set; }
    public string Message { get; set; } = string.Empty;
}
