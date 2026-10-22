using FeedbackService.Domain.Entities;
using FeedbackService.Domain.Enums;

namespace FeedbackService.DTOs;

public class ModerationHistoryResponse
{
    public string ModeratorUuid { get; set; } = string.Empty;
    public ModerationStatus PreviousStatus { get; set; }
    public ModerationStatus NewStatus { get; set; }
    public string Reason { get; set; } = string.Empty;
    public DateTime ModeratedAt { get; set; }

    public static ModerationHistoryResponse FromEntity(FeedbackModerationHistory h)
    {
        return new ModerationHistoryResponse
        {
            ModeratorUuid = h.ModeratorUuid,
            PreviousStatus = h.PreviousStatus,
            NewStatus = h.NewStatus,
            Reason = h.Reason,
            ModeratedAt = h.ModeratedAt
        };
    }
}
