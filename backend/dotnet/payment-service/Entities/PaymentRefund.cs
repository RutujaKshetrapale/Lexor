using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using PaymentService.Enums;

namespace PaymentService.Entities;

[Table("payment_refunds")]
public class PaymentRefund
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [MaxLength(36)]
    [Column("refund_uuid")]
    public string RefundUuid { get; set; } = Guid.NewGuid().ToString();

    [Required]
    [Column("payment_id")]
    public long PaymentId { get; set; }

    [ForeignKey("PaymentId")]
    public virtual Payment? Payment { get; set; }

    [Required]
    [MaxLength(36)]
    [Column("payment_uuid")]
    public string PaymentUuid { get; set; } = string.Empty;

    [Required]
    [Column("amount", TypeName = "decimal(10,2)")]
    public decimal Amount { get; set; }

    [Required]
    [Column("amount_paise")]
    public long AmountPaise { get; set; }

    [Required]
    [MaxLength(3)]
    [Column("currency")]
    public string Currency { get; set; } = "INR";

    [Required]
    [MaxLength(255)]
    [Column("reason")]
    public string Reason { get; set; } = string.Empty;

    [Required]
    [MaxLength(30)]
    [Column("status")]
    public RefundStatus Status { get; set; } = RefundStatus.REQUESTED;

    [MaxLength(100)]
    [Column("provider_refund_id")]
    public string? ProviderRefundId { get; set; }

    [Required]
    [MaxLength(50)]
    [Column("initiated_by")]
    public string InitiatedBy { get; set; } = "RIDER";

    [MaxLength(500)]
    [Column("admin_notes")]
    public string? AdminNotes { get; set; }

    [Column("processed_at")]
    public DateTime? ProcessedAt { get; set; }

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
