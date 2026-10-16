namespace RideRequestService.DTOs.Response;

public class FareEstimateResponse
{
    public decimal DistanceKm { get; set; }
    public int DurationMinutes { get; set; }
    public decimal BaseFare { get; set; }
    public decimal DistanceFare { get; set; }
    public decimal TimeFare { get; set; }
    public decimal SubtotalFare { get; set; }
    public decimal MinimumFare { get; set; }
    public decimal FinalEstimatedFare { get; set; }
    public string Currency { get; set; } = "INR";
}
