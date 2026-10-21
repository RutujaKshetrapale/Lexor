namespace PaymentService.Clients.DTOs;

public class BookingDetailDto
{
    public string BookingUuid { get; set; } = string.Empty;
    public string Status { get; set; } = string.Empty;
    public string RiderUuid { get; set; } = string.Empty;
    public string RiderName { get; set; } = string.Empty;
    public string? DriverUuid { get; set; }
    public string DriverName { get; set; } = string.Empty;
    public string VehicleModel { get; set; } = string.Empty;
    public string LicensePlate { get; set; } = string.Empty;
    public decimal TotalFare { get; set; }
    public string Currency { get; set; } = "INR";
    public string PickupAddress { get; set; } = string.Empty;
    public string DropAddress { get; set; } = string.Empty;
    public decimal BaseFare { get; set; } = 50.00m;
    public decimal DistanceFare { get; set; } = 150.00m;
    public decimal TimeFare { get; set; } = 30.00m;
    public decimal TaxAmount { get; set; } = 20.00m;
}
