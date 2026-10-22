namespace FeedbackService.Domain.Enums;

public enum ModerationStatus
{
    VISIBLE,
    UNDER_REVIEW,
    HIDDEN,
    REMOVED
}

public static class ModerationStatusExtensions
{
    public static bool CanTransitionTo(this ModerationStatus current, ModerationStatus next)
    {
        if (current == next) return false;
        return current switch
        {
            ModerationStatus.VISIBLE => next is ModerationStatus.UNDER_REVIEW or ModerationStatus.HIDDEN or ModerationStatus.REMOVED,
            ModerationStatus.UNDER_REVIEW => next is ModerationStatus.VISIBLE or ModerationStatus.HIDDEN or ModerationStatus.REMOVED,
            ModerationStatus.HIDDEN => next is ModerationStatus.VISIBLE or ModerationStatus.REMOVED,
            ModerationStatus.REMOVED => next == ModerationStatus.VISIBLE,
            _ => false
        };
    }
}
