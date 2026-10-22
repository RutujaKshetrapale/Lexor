using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace FeedbackService.Domain.Entities;

[Table("feedback_tag_assignments")]
public class FeedbackTagAssignment
{
    [Key]
    [Column("id")]
    public long Id { get; set; }

    [Column("feedback_id")]
    public long FeedbackId { get; set; }

    [ForeignKey("FeedbackId")]
    public RideFeedback Feedback { get; set; } = null!;

    [Column("tag_id")]
    public long TagId { get; set; }

    [ForeignKey("TagId")]
    public FeedbackTag Tag { get; set; } = null!;

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
