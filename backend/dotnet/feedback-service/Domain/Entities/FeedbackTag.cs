using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using FeedbackService.Domain.Enums;

namespace FeedbackService.Domain.Entities;

[Table("feedback_tags")]
public class FeedbackTag
{
    [Key]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [MaxLength(50)]
    [Column("tag_code")]
    public string TagCode { get; set; } = string.Empty;

    [Required]
    [MaxLength(100)]
    [Column("label")]
    public string Label { get; set; } = string.Empty;

    [Column("target_role")]
    public TagTargetRole TargetRole { get; set; } = TagTargetRole.BOTH;

    [Column("category")]
    public TagCategory Category { get; set; } = TagCategory.GENERAL;

    [Column("is_active")]
    public bool IsActive { get; set; } = true;

    [Column("display_order")]
    public int DisplayOrder { get; set; } = 0;

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public List<FeedbackTagAssignment> TagAssignments { get; set; } = new();
}
