namespace BookingService.Clients.DTOs;

public class RideRequestClientDto
{
    public string RequestUuid { get; set; } = string.Empty;
    public string RiderUuid { get; set; } = string.Empty;
    public string? RiderName { get; set; }
    public string? RiderEmail { get; set; }
    public string PickupAddress { get; set; } = string.Empty;
    public decimal PickupLatitude { get; set; }
    public decimal PickupLongitude { get; set; }
    public string DropAddress { get; set; } = string.Empty;
    public decimal DropLatitude { get; set; }
    public decimal DropLongitude { get; set; }
    public string? RideTypeCode { get; set; }
    public string? RideTypeName { get; set; }
    public decimal? EstimatedDistanceKm { get; set; }
    public int? EstimatedDurationMinutes { get; set; }
    public decimal? EstimatedFare { get; set; }
    public string Currency { get; set; } = "INR";
    public string Status { get; set; } = string.Empty;
    public DateTime RequestedAt { get; set; }
}
