using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace DriverService.Entities;

[Table("driver_verification_history")]
public class DriverVerificationHistory
{
    [Key]
    [Column("id")]
    public long Id { get; set; }

    [Column("driver_id")]
    public long DriverId { get; set; }

    [ForeignKey(nameof(DriverId))]
    public DriverProfile Driver { get; set; } = null!;

    [Column("document_id")]
    public long? DocumentId { get; set; }

    [ForeignKey(nameof(DocumentId))]
    public DriverDocument? Document { get; set; }

    [Required]
    [Column("action_type")]
    [StringLength(50)]
    public string ActionType { get; set; } = string.Empty;

    [Required]
    [Column("previous_status")]
    [StringLength(50)]
    public string PreviousStatus { get; set; } = string.Empty;

    [Required]
    [Column("new_status")]
    [StringLength(50)]
    public string NewStatus { get; set; } = string.Empty;

    [Column("reviewed_by_admin_id")]
    public long ReviewedByAdminId { get; set; }

    [ForeignKey(nameof(ReviewedByAdminId))]
    public User ReviewedByAdmin { get; set; } = null!;

    [Column("reason")]
    [StringLength(500)]
    public string? Reason { get; set; }

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
