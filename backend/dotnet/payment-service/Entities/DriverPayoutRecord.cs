using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using PaymentService.Enums;

namespace PaymentService.Entities;

[Table("driver_payout_records")]
public class DriverPayoutRecord
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [MaxLength(36)]
    [Column("payout_uuid")]
    public string PayoutUuid { get; set; } = Guid.NewGuid().ToString();

    [Required]
    [MaxLength(36)]
    [Column("driver_uuid")]
    public string DriverUuid { get; set; } = string.Empty;

    [Required]
    [Column("period_start")]
    public DateTime PeriodStart { get; set; }

    [Required]
    [Column("period_end")]
    public DateTime PeriodEnd { get; set; }

    [Column("gross_earnings", TypeName = "decimal(10,2)")]
    public decimal GrossEarnings { get; set; } = 0m;

    [Column("platform_commission", TypeName = "decimal(10,2)")]
    public decimal PlatformCommission { get; set; } = 0m;

    [Column("cash_collected", TypeName = "decimal(10,2)")]
    public decimal CashCollected { get; set; } = 0m;

    [Column("net_payout_due", TypeName = "decimal(10,2)")]
    public decimal NetPayoutDue { get; set; } = 0m;

    [Required]
    [MaxLength(3)]
    [Column("currency")]
    public string Currency { get; set; } = "INR";

    [Required]
    [MaxLength(30)]
    [Column("status")]
    public PayoutStatus Status { get; set; } = PayoutStatus.CALCULATED;

    [MaxLength(100)]
    [Column("provider_payout_reference")]
    public string? ProviderPayoutReference { get; set; }

    [MaxLength(500)]
    [Column("notes")]
    public string? Notes { get; set; }

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
