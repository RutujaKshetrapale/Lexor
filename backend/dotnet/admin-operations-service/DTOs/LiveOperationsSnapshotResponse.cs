namespace AdminOperationsService.DTOs;

public class LiveOperationsSnapshotResponse
{
    public DateTime SnapshotTimestamp { get; set; } = DateTime.UtcNow;
    public int RefreshIntervalSeconds { get; set; } = 10;
    public DriverStateBreakdown DriverStates { get; set; } = new();
    public List<ActiveRideItem> ActiveRides { get; set; } = new();
    public List<PendingRequestItem> PendingRequests { get; set; } = new();
    public List<RecentCancellationItem> RecentCancellations { get; set; } = new();
    public List<OperationalAlert> ActiveAlerts { get; set; } = new();
}

public class DriverStateBreakdown
{
    public long OnlineAvailable { get; set; }
    public long BusyInTrip { get; set; }
    public long Offline { get; set; }
    public long TotalDrivers { get; set; }
}

public class ActiveRideItem
{
    public string? BookingUuid { get; set; }
    public string? RideUuid { get; set; }
    public string? RiderUuid { get; set; }
    public string? DriverUuid { get; set; }
    public string? PickupAddress { get; set; }
    public string? DropoffAddress { get; set; }
    public string? Status { get; set; }
    public decimal EstimatedFare { get; set; }
    public DateTime StartTime { get; set; }
}

public class PendingRequestItem
{
    public string? RequestUuid { get; set; }
    public string? RiderUuid { get; set; }
    public string? RideType { get; set; }
    public string? PickupAddress { get; set; }
    public string? DropoffAddress { get; set; }
    public string? Status { get; set; }
    public int MatchingAttemptsCount { get; set; }
    public DateTime RequestedAt { get; set; }
    public long WaitingSeconds { get; set; }
}

public class RecentCancellationItem
{
    public string? BookingUuid { get; set; }
    public string? CancelledBy { get; set; }
    public string? Reason { get; set; }
    public DateTime CancelledAt { get; set; }
}

public class OperationalAlert
{
    public string AlertType { get; set; } = string.Empty;
    public string Severity { get; set; } = "INFO";
    public string Message { get; set; } = string.Empty;
    public DateTime DetectedAt { get; set; } = DateTime.UtcNow;
}
