using DriverService.Enums;

namespace DriverService.DTOs.Request;

public class DriverAvailabilityRequest
{
    public DriverStatus? Status { get; set; }
    public bool? IsOnline { get; set; }
}
