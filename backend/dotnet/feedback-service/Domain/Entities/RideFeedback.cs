using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using FeedbackService.Domain.Enums;

namespace FeedbackService.Domain.Entities;

[Table("ride_feedback")]
public class RideFeedback
{
    [Key]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [MaxLength(36)]
    [Column("feedback_uuid")]
    public string FeedbackUuid { get; set; } = Guid.NewGuid().ToString();

    [Required]
    [MaxLength(36)]
    [Column("ride_uuid")]
    public string RideUuid { get; set; } = string.Empty;

    [MaxLength(36)]
    [Column("booking_uuid")]
    public string? BookingUuid { get; set; }

    [Required]
    [MaxLength(36)]
    [Column("reviewer_uuid")]
    public string ReviewerUuid { get; set; } = string.Empty;

    [Required]
    [MaxLength(36)]
    [Column("target_uuid")]
    public string TargetUuid { get; set; } = string.Empty;

    [Column("reviewer_role")]
    public FeedbackRole ReviewerRole { get; set; }

    [Column("target_role")]
    public FeedbackRole TargetRole { get; set; }

    [Range(1, 5)]
    [Column("rating")]
    public int Rating { get; set; }

    [MaxLength(1000)]
    [Column("review_text")]
    public string? ReviewText { get; set; }

    [Column("moderation_status")]
    public ModerationStatus ModerationStatus { get; set; } = ModerationStatus.VISIBLE;

    [Column("is_edited")]
    public bool IsEdited { get; set; } = false;

    [Column("editable_until")]
    public DateTime? EditableUntil { get; set; }

    [ConcurrencyCheck]
    [Column("version")]
    public long Version { get; set; } = 0;

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    public List<FeedbackTagAssignment> TagAssignments { get; set; } = new();
    public List<FeedbackReport> Reports { get; set; } = new();
    public List<FeedbackModerationHistory> ModerationHistories { get; set; } = new();
}
