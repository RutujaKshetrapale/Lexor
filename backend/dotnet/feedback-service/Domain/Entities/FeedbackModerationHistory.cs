using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using FeedbackService.Domain.Enums;

namespace FeedbackService.Domain.Entities;

[Table("feedback_moderation_history")]
public class FeedbackModerationHistory
{
    [Key]
    [Column("id")]
    public long Id { get; set; }

    [Column("feedback_id")]
    public long FeedbackId { get; set; }

    [ForeignKey("FeedbackId")]
    public RideFeedback Feedback { get; set; } = null!;

    [Required]
    [MaxLength(36)]
    [Column("moderator_uuid")]
    public string ModeratorUuid { get; set; } = string.Empty;

    [Column("previous_status")]
    public ModerationStatus PreviousStatus { get; set; }

    [Column("new_status")]
    public ModerationStatus NewStatus { get; set; }

    [Required]
    [MaxLength(500)]
    [Column("reason")]
    public string Reason { get; set; } = string.Empty;

    [Column("moderated_at")]
    public DateTime ModeratedAt { get; set; } = DateTime.UtcNow;
}
