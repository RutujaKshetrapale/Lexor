using BookingService.Enums;

namespace BookingService.DTOs.Response;

public class BookingResponse
{
    public string BookingUuid { get; set; } = string.Empty;
    public string RideRequestUuid { get; set; } = string.Empty;
    public BookingStatus Status { get; set; }
    public RiderSummaryDto? Rider { get; set; }
    public DriverSummaryDto? Driver { get; set; }
    public VehicleSummaryDto? Vehicle { get; set; }
    public RideTypeSummaryDto? RideType { get; set; }
    public string? PickupAddress { get; set; }
    public decimal? PickupLatitude { get; set; }
    public decimal? PickupLongitude { get; set; }
    public string? DropAddress { get; set; }
    public decimal? DropLatitude { get; set; }
    public decimal? DropLongitude { get; set; }
    public decimal? EstimatedDistanceKm { get; set; }
    public int? EstimatedDurationMinutes { get; set; }
    public decimal? TotalFare { get; set; }
    public string Currency { get; set; } = "INR";
    public string? Otp { get; set; }
    public bool? OtpVerified { get; set; }
    public DateTime? ScheduledTime { get; set; }
    public string? CancellationReason { get; set; }
    public string? CancelledBy { get; set; }
    public DateTime? AcceptedAt { get; set; }
    public DateTime? ArrivedAt { get; set; }
    public DateTime? StartedAt { get; set; }
    public DateTime? CompletedAt { get; set; }
    public DateTime? CancelledAt { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
}
