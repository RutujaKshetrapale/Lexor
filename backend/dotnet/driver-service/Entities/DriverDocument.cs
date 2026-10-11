using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using DriverService.Enums;

namespace DriverService.Entities;

[Table("driver_documents")]
public class DriverDocument
{
    [Key]
    [Column("id")]
    public long Id { get; set; }

    [Column("driver_id")]
    public long DriverId { get; set; }

    [ForeignKey(nameof(DriverId))]
    public DriverProfile Driver { get; set; } = null!;

    [Required]
    [Column("document_type")]
    public DocumentType DocumentType { get; set; }

    [Column("document_number")]
    [StringLength(100)]
    public string? DocumentNumber { get; set; }

    [Required]
    [Column("file_name")]
    [StringLength(255)]
    public string FileName { get; set; } = string.Empty;

    [Required]
    [Column("file_path")]
    [StringLength(500)]
    public string FilePath { get; set; } = string.Empty;

    [Required]
    [Column("content_type")]
    [StringLength(100)]
    public string ContentType { get; set; } = string.Empty;

    [Column("file_size")]
    public long FileSize { get; set; }

    [Column("issue_date")]
    public DateOnly? IssueDate { get; set; }

    [Column("expiry_date")]
    public DateOnly? ExpiryDate { get; set; }

    [Column("issuing_authority")]
    [StringLength(100)]
    public string? IssuingAuthority { get; set; }

    [Column("verification_status")]
    public DocumentVerificationStatus VerificationStatus { get; set; } = DocumentVerificationStatus.PENDING_REVIEW;

    [Column("rejection_reason")]
    [StringLength(500)]
    public string? RejectionReason { get; set; }

    [Column("review_notes")]
    [StringLength(500)]
    public string? ReviewNotes { get; set; }

    [Column("reviewed_by_admin_id")]
    public long? ReviewedByAdminId { get; set; }

    [ForeignKey(nameof(ReviewedByAdminId))]
    public User? ReviewedByAdmin { get; set; }

    [Column("reviewed_at")]
    public DateTime? ReviewedAt { get; set; }

    [Column("version")]
    public int Version { get; set; } = 1;

    [Column("is_current")]
    public bool IsCurrent { get; set; } = true;

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
