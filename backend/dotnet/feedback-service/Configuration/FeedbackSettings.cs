namespace FeedbackService.Configuration;

public class FeedbackSettings
{
    public int EditWindowMinutes { get; set; } = 120;
    public int SubmissionWindowDays { get; set; } = 7;
    public bool EditEnabled { get; set; } = true;
    public int ReportAutoReviewThreshold { get; set; } = 3;
    public string InternalServiceKey { get; set; } = "lexor-internal-service-secret-key-2026";
    public string BookingServiceUrl { get; set; } = "http://localhost:8096";
    public string NotificationServiceUrl { get; set; } = "http://localhost:8100";
}
