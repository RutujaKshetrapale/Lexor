namespace FeedbackService.DTOs;

public class DirectNotificationRequest
{
    public string RecipientUuid { get; set; } = string.Empty;
    public string RecipientRole { get; set; } = "DRIVER";
    public string NotificationType { get; set; } = string.Empty;
    public string Category { get; set; } = "RIDE";
    public string Title { get; set; } = string.Empty;
    public string Body { get; set; } = string.Empty;
    public string? DeepLink { get; set; }
    public string? RelatedEntityType { get; set; }
    public string? RelatedEntityUuid { get; set; }
    public string? IdempotencyKey { get; set; }
    public List<string>? Channels { get; set; }
}
