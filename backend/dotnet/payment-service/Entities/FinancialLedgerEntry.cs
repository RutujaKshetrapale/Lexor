using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using PaymentService.Enums;

namespace PaymentService.Entities;

[Table("financial_ledger_entries")]
public class FinancialLedgerEntry
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [MaxLength(36)]
    [Column("entry_uuid")]
    public string EntryUuid { get; set; } = Guid.NewGuid().ToString();

    [Required]
    [MaxLength(36)]
    [Column("payment_uuid")]
    public string PaymentUuid { get; set; } = string.Empty;

    [Required]
    [MaxLength(36)]
    [Column("booking_uuid")]
    public string BookingUuid { get; set; } = string.Empty;

    [MaxLength(36)]
    [Column("driver_uuid")]
    public string? DriverUuid { get; set; }

    [Required]
    [MaxLength(40)]
    [Column("entry_type")]
    public LedgerEntryType EntryType { get; set; }

    [Required]
    [MaxLength(10)]
    [Column("direction")]
    public LedgerDirection Direction { get; set; }

    [Required]
    [MaxLength(40)]
    [Column("account")]
    public LedgerAccount Account { get; set; }

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
    [Column("description")]
    public string Description { get; set; } = string.Empty;

    [MaxLength(100)]
    [Column("reference_id")]
    public string? ReferenceId { get; set; }

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
