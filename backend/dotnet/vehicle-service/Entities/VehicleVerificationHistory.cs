using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace VehicleService.Entities;

[Table("vehicle_verification_history")]
public class VehicleVerificationHistory
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [Column("vehicle_id")]
    public long VehicleId { get; set; }

    [ForeignKey("VehicleId")]
    public virtual Vehicle Vehicle { get; set; } = null!;

    [Column("document_id")]
    public long? DocumentId { get; set; }

    [ForeignKey("DocumentId")]
    public virtual VehicleDocument? Document { get; set; }

    [Required]
    [MaxLength(50)]
    [Column("action_type")]
    public string ActionType { get; set; } = string.Empty;

    [Required]
    [MaxLength(50)]
    [Column("previous_status")]
    public string PreviousStatus { get; set; } = string.Empty;

    [Required]
    [MaxLength(50)]
    [Column("new_status")]
    public string NewStatus { get; set; } = string.Empty;

    [Required]
    [Column("reviewed_by_admin_id")]
    public long ReviewedByAdminId { get; set; }

    [ForeignKey("ReviewedByAdminId")]
    public virtual User ReviewedByAdmin { get; set; } = null!;

    [MaxLength(500)]
    [Column("reason")]
    public string? Reason { get; set; }

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
