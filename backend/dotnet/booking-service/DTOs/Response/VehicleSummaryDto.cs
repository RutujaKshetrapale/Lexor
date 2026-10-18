namespace BookingService.DTOs.Response;

public class VehicleSummaryDto
{
    public string VehicleUuid { get; set; } = string.Empty;
    public string? Make { get; set; }
    public string? Model { get; set; }
    public int? Year { get; set; }
    public string? Color { get; set; }
    public string? LicensePlate { get; set; }
}
