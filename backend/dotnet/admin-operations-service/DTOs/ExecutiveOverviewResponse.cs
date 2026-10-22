namespace AdminOperationsService.DTOs;

public class ExecutiveOverviewResponse
{
    public long TotalRides { get; set; }
    public long CompletedRides { get; set; }
    public long CancelledRides { get; set; }
    public long ActiveRides { get; set; }
    public long PendingRideRequests { get; set; }
    public long OnlineDrivers { get; set; }
    public long InTripDrivers { get; set; }
    public long PendingDriverVerifications { get; set; }
    public long RegisteredRiders { get; set; }
    public long RegisteredDrivers { get; set; }
    public long RegisteredVehicles { get; set; }
    public decimal GrossBookingValue { get; set; }
    public decimal SuccessfulPaymentTotals { get; set; }
    public decimal RefundTotals { get; set; }
    public decimal PlatformCommissionEarned { get; set; }
    public decimal AverageDriverRating { get; set; }
    public long FeedbackReportsAwaitingModeration { get; set; }

    public string TimeRange { get; set; } = "TODAY";
    public DateTime RangeStartDate { get; set; }
    public DateTime RangeEndDate { get; set; }
    public string TimeZone { get; set; } = "UTC";
    public DateTime CalculatedAt { get; set; } = DateTime.UtcNow;

    public Dictionary<string, string> MetricDefinitions { get; set; } = new();
}
