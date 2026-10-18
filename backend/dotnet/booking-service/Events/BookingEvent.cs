namespace BookingService.Events;

public class BookingEvent
{
    public string EventId { get; set; } = Guid.NewGuid().ToString();
    public string EventType { get; set; } = string.Empty;
    public string BookingUuid { get; set; } = string.Empty;
    public string? RideRequestUuid { get; set; }
    public string? RiderUserUuid { get; set; }
    public string? DriverUserUuid { get; set; }
    public string Status { get; set; } = string.Empty;
    public DateTime Timestamp { get; set; } = DateTime.UtcNow;
    public IDictionary<string, object>? Metadata { get; set; }
}
