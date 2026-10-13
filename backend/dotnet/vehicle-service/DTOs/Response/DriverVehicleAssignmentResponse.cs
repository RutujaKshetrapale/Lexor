using VehicleService.Enums;

namespace VehicleService.DTOs.Response;

public class DriverVehicleAssignmentResponse
{
    public long Id { get; set; }
    public string VehicleUuid { get; set; } = string.Empty;
    public string VehiclePlate { get; set; } = string.Empty;
    public string? DriverUuid { get; set; }
    public string? DriverName { get; set; }
    public string? AssignedByAdminName { get; set; }
    public AssignmentStatus Status { get; set; }
    public DateTime AssignedAt { get; set; }
    public DateTime? UnassignedAt { get; set; }
    public string? Reason { get; set; }
}
