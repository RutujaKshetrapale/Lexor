using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using VehicleService.Enums;

namespace VehicleService.Entities;

[Table("vehicle_documents")]
public class VehicleDocument
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

    [Required]
    [Column("document_type")]
    public VehicleDocumentType DocumentType { get; set; }

    [MaxLength(100)]
    [Column("document_number")]
    public string? DocumentNumber { get; set; }

    [Required]
    [MaxLength(255)]
    [Column("file_name")]
    public string FileName { get; set; } = string.Empty;

    [Required]
    [MaxLength(500)]
    [Column("file_path")]
    public string FilePath { get; set; } = string.Empty;

    [Required]
    [MaxLength(100)]
    [Column("content_type")]
    public string ContentType { get; set; } = string.Empty;

    [Required]
    [Column("file_size")]
    public long FileSize { get; set; }

    [Column("issue_date")]
    public DateOnly? IssueDate { get; set; }

    [Column("expiry_date")]
    public DateOnly? ExpiryDate { get; set; }

    [MaxLength(100)]
    [Column("issuing_authority")]
    public string? IssuingAuthority { get; set; }

    [Required]
    [Column("verification_status")]
    public DocumentVerificationStatus VerificationStatus { get; set; } = DocumentVerificationStatus.PENDING_REVIEW;

    [MaxLength(500)]
    [Column("rejection_reason")]
    public string? RejectionReason { get; set; }

    [MaxLength(500)]
    [Column("review_notes")]
    public string? ReviewNotes { get; set; }

    [Column("reviewed_by_admin_id")]
    public long? ReviewedByAdminId { get; set; }

    [ForeignKey("ReviewedByAdminId")]
    public virtual User? ReviewedByAdmin { get; set; }

    [Column("reviewed_at")]
    public DateTime? ReviewedAt { get; set; }

    [Required]
    [Column("version")]
    public int Version { get; set; } = 1;

    [Required]
    [Column("is_current")]
    public bool IsCurrent { get; set; } = true;

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
