using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using PaymentService.Enums;

namespace PaymentService.Entities;

[Table("payment_webhook_events")]
public class PaymentWebhookEvent
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [MaxLength(36)]
    [Column("event_uuid")]
    public string EventUuid { get; set; } = Guid.NewGuid().ToString();

    [Required]
    [MaxLength(50)]
    [Column("provider")]
    public string Provider { get; set; } = string.Empty;

    [Required]
    [MaxLength(100)]
    [Column("provider_event_id")]
    public string ProviderEventId { get; set; } = string.Empty;

    [Required]
    [MaxLength(100)]
    [Column("event_type")]
    public string EventType { get; set; } = string.Empty;

    [Required]
    [Column("payload")]
    public string Payload { get; set; } = string.Empty;

    [MaxLength(255)]
    [Column("signature")]
    public string? Signature { get; set; }

    [Required]
    [MaxLength(30)]
    [Column("status")]
    public WebhookStatus Status { get; set; } = WebhookStatus.RECEIVED;

    [Column("processing_error")]
    public string? ProcessingError { get; set; }

    [Column("received_at")]
    public DateTime ReceivedAt { get; set; } = DateTime.UtcNow;

    [Column("processed_at")]
    public DateTime? ProcessedAt { get; set; }
}
