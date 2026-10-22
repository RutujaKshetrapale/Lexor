using FeedbackService.Domain.Entities;
using FeedbackService.Domain.Enums;

namespace FeedbackService.DTOs;

public class FeedbackResponse
{
    public string FeedbackUuid { get; set; } = string.Empty;
    public string RideUuid { get; set; } = string.Empty;
    public string? BookingUuid { get; set; }
    public string ReviewerUuid { get; set; } = string.Empty;
    public string TargetUuid { get; set; } = string.Empty;
    public FeedbackRole ReviewerRole { get; set; }
    public FeedbackRole TargetRole { get; set; }
    public int Rating { get; set; }
    public string? ReviewText { get; set; }
    public List<FeedbackTagDto> Tags { get; set; } = new();
    public ModerationStatus ModerationStatus { get; set; }
    public bool IsEdited { get; set; }
    public DateTime? EditableUntil { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }

    public static FeedbackResponse FromEntity(RideFeedback f)
    {
        return new FeedbackResponse
        {
            FeedbackUuid = f.FeedbackUuid,
            RideUuid = f.RideUuid,
            BookingUuid = f.BookingUuid,
            ReviewerUuid = f.ReviewerUuid,
            TargetUuid = f.TargetUuid,
            ReviewerRole = f.ReviewerRole,
            TargetRole = f.TargetRole,
            Rating = f.Rating,
            ReviewText = f.ReviewText,
            Tags = f.TagAssignments?.Select(a => FeedbackTagDto.FromEntity(a.Tag)).ToList() ?? new List<FeedbackTagDto>(),
            ModerationStatus = f.ModerationStatus,
            IsEdited = f.IsEdited,
            EditableUntil = f.EditableUntil,
            CreatedAt = f.CreatedAt,
            UpdatedAt = f.UpdatedAt
        };
    }
}
