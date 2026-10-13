using System.Text.Json.Serialization;
using VehicleService.Enums;

namespace VehicleService.DTOs.Response;

public class VehicleResponse
{
    public string Uuid { get; set; } = string.Empty;
    public string? DriverUuid { get; set; }
    public string? DriverName { get; set; }
    public string Make { get; set; } = string.Empty;
    public string Model { get; set; } = string.Empty;
    public int Year { get; set; }
    public string Color { get; set; } = string.Empty;
    public string LicensePlate { get; set; } = string.Empty;
    public string? Vin { get; set; }
    public string RideTypeCategory { get; set; } = string.Empty;
    public int SeatCapacity { get; set; }
    public FuelType FuelType { get; set; }
    public bool IsActive { get; set; }
    public bool IsInspected { get; set; }
    public DateOnly? InspectionExpiresAt { get; set; }
    public VehicleVerificationStatus VerificationStatus { get; set; }
    public string? RejectionReason { get; set; }
    public DateTime? VerifiedAt { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
}
