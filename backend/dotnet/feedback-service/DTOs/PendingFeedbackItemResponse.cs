namespace FeedbackService.DTOs;

public class PendingFeedbackItemResponse
{
    public string RideUuid { get; set; } = string.Empty;
    public string? BookingUuid { get; set; }
    public string TargetUuid { get; set; } = string.Empty;
    public string TargetName { get; set; } = string.Empty;
    public string TargetRole { get; set; } = "DRIVER";
    public DateTime CompletedAt { get; set; }
    public DateTime SubmissionDeadline { get; set; }
}
