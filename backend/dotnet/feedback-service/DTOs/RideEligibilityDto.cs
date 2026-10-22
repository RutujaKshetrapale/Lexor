namespace FeedbackService.DTOs;

public class RideEligibilityDto
{
    public string RideUuid { get; set; } = string.Empty;
    public string? BookingUuid { get; set; }
    public string Status { get; set; } = string.Empty;
    public string RiderUuid { get; set; } = string.Empty;
    public string DriverUuid { get; set; } = string.Empty;
    public DateTime CompletedAt { get; set; } = DateTime.UtcNow;
    public string? RiderName { get; set; }
    public string? DriverName { get; set; }

    public bool IsAuthoritativeCompleted()
    {
        if (string.IsNullOrWhiteSpace(Status)) return false;
        var s = Status.Trim().ToUpperInvariant();
        return s is "COMPLETED" or "TRIP_COMPLETED" or "PAYMENT_COMPLETED" or "FEEDBACK";
    }
}
