using RideRequestService.Enums;

namespace RideRequestService.DTOs.Response;

public class RideRequestResponse
{
    public string RequestUuid { get; set; } = string.Empty;
    public string RiderUuid { get; set; } = string.Empty;
    public string RiderName { get; set; } = string.Empty;
    public string RiderEmail { get; set; } = string.Empty;
    public string PickupAddress { get; set; } = string.Empty;
    public decimal PickupLatitude { get; set; }
    public decimal PickupLongitude { get; set; }
    public string DropAddress { get; set; } = string.Empty;
    public decimal DropLatitude { get; set; }
    public decimal DropLongitude { get; set; }
    public string RideTypeCode { get; set; } = string.Empty;
    public string RideTypeName { get; set; } = string.Empty;
    public decimal EstimatedDistanceKm { get; set; }
    public int? EstimatedDurationMinutes { get; set; }
    public decimal EstimatedFare { get; set; }
    public string Currency { get; set; } = "INR";
    public RideRequestStatus Status { get; set; }
    public DateTime RequestedAt { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
}
