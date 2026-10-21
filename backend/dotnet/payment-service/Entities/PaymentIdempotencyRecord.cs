using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace PaymentService.Entities;

[Table("payment_idempotency_records")]
public class PaymentIdempotencyRecord
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [MaxLength(100)]
    [Column("idempotency_key")]
    public string IdempotencyKey { get; set; } = string.Empty;

    [Required]
    [MaxLength(36)]
    [Column("caller_uuid")]
    public string CallerUuid { get; set; } = string.Empty;

    [Required]
    [MaxLength(50)]
    [Column("resource_type")]
    public string ResourceType { get; set; } = string.Empty;

    [Required]
    [Column("response_status_code")]
    public int ResponseStatusCode { get; set; }

    [Required]
    [Column("response_payload")]
    public string ResponsePayload { get; set; } = string.Empty;

    [Required]
    [Column("expires_at")]
    public DateTime ExpiresAt { get; set; } = DateTime.UtcNow.AddHours(24);

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
