namespace DriverMatchingService.DTOs.Response;

public class VehicleSummaryResponse
{
    public string VehicleUuid { get; set; } = string.Empty;
    public string Make { get; set; } = string.Empty;
    public string Model { get; set; } = string.Empty;
    public string Color { get; set; } = string.Empty;
    public string LicensePlate { get; set; } = string.Empty;
    public int SeatCapacity { get; set; }
    public string FuelType { get; set; } = string.Empty;
    public string RideTypeCategory { get; set; } = string.Empty;
}
