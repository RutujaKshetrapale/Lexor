using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using FeedbackService.Domain.Enums;

namespace FeedbackService.Domain.Entities;

[Table("feedback_reports")]
public class FeedbackReport
{
    [Key]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [MaxLength(36)]
    [Column("report_uuid")]
    public string ReportUuid { get; set; } = Guid.NewGuid().ToString();

    [Column("feedback_id")]
    public long FeedbackId { get; set; }

    [ForeignKey("FeedbackId")]
    public RideFeedback Feedback { get; set; } = null!;

    [Required]
    [MaxLength(36)]
    [Column("reporter_uuid")]
    public string ReporterUuid { get; set; } = string.Empty;

    [Column("reason")]
    public ReportReason Reason { get; set; }

    [MaxLength(500)]
    [Column("comments")]
    public string? Comments { get; set; }

    [Column("status")]
    public ReportStatus Status { get; set; } = ReportStatus.PENDING;

    [MaxLength(500)]
    [Column("resolution_notes")]
    public string? ResolutionNotes { get; set; }

    [MaxLength(36)]
    [Column("resolved_by")]
    public string? ResolvedBy { get; set; }

    [Column("resolved_at")]
    public DateTime? ResolvedAt { get; set; }

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
