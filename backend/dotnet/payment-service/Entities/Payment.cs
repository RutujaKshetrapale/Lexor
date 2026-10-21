using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using PaymentService.Enums;

namespace PaymentService.Entities;

[Table("payments")]
public class Payment
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [MaxLength(36)]
    [Column("payment_uuid")]
    public string PaymentUuid { get; set; } = Guid.NewGuid().ToString();

    [Required]
    [MaxLength(36)]
    [Column("booking_uuid")]
    public string BookingUuid { get; set; } = string.Empty;

    [Required]
    [MaxLength(36)]
    [Column("rider_uuid")]
    public string RiderUuid { get; set; } = string.Empty;

    [MaxLength(36)]
    [Column("driver_uuid")]
    public string? DriverUuid { get; set; }

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
    [MaxLength(30)]
    [Column("payment_method")]
    public PaymentMethod PaymentMethod { get; set; }

    [Required]
    [MaxLength(30)]
    [Column("status")]
    public PaymentStatus Status { get; set; } = PaymentStatus.CREATED;

    [Required]
    [MaxLength(50)]
    [Column("provider")]
    public string Provider { get; set; } = "RAZORPAY";

    [MaxLength(100)]
    [Column("provider_order_id")]
    public string? ProviderOrderId { get; set; }

    [MaxLength(100)]
    [Column("provider_payment_id")]
    public string? ProviderPaymentId { get; set; }

    [MaxLength(255)]
    [Column("provider_signature")]
    public string? ProviderSignature { get; set; }

    [MaxLength(500)]
    [Column("failure_reason")]
    public string? FailureReason { get; set; }

    [MaxLength(100)]
    [Column("idempotency_key")]
    public string? IdempotencyKey { get; set; }

    [ConcurrencyCheck]
    [Column("version")]
    public long Version { get; set; } = 0;

    [Column("authorized_at")]
    public DateTime? AuthorizedAt { get; set; }

    [Column("captured_at")]
    public DateTime? CapturedAt { get; set; }

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    public virtual ICollection<PaymentTransaction> Transactions { get; set; } = new List<PaymentTransaction>();
    public virtual ICollection<PaymentRefund> Refunds { get; set; } = new List<PaymentRefund>();
}
