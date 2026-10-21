using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using PaymentService.Enums;

namespace PaymentService.Entities;

[Table("payment_transactions")]
public class PaymentTransaction
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [MaxLength(36)]
    [Column("transaction_uuid")]
    public string TransactionUuid { get; set; } = Guid.NewGuid().ToString();

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
    [MaxLength(30)]
    [Column("attempt_type")]
    public TransactionAttemptType AttemptType { get; set; }

    [Required]
    [MaxLength(50)]
    [Column("gateway_provider")]
    public string GatewayProvider { get; set; } = "RAZORPAY";

    [MaxLength(100)]
    [Column("gateway_order_id")]
    public string? GatewayOrderId { get; set; }

    [MaxLength(100)]
    [Column("gateway_payment_id")]
    public string? GatewayPaymentId { get; set; }

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
    [Column("status")]
    public TransactionStatus Status { get; set; }

    [MaxLength(100)]
    [Column("error_code")]
    public string? ErrorCode { get; set; }

    [Column("error_message")]
    public string? ErrorMessage { get; set; }

    [Column("raw_payload")]
    public string? RawPayload { get; set; }

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
