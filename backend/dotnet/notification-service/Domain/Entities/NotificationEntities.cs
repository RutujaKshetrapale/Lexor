using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using NotificationService.Domain.Enums;

namespace NotificationService.Domain.Entities;

[Table("notifications")]
public class Notification
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    public long Id { get; set; }

    [Required]
    [MaxLength(36)]
    public string NotificationUuid { get; set; } = Guid.NewGuid().ToString();

    [Required]
    [MaxLength(36)]
    public string RecipientUuid { get; set; } = string.Empty;

    [Required]
    public RecipientRole RecipientRole { get; set; } = RecipientRole.RIDER;

    [Required]
    [MaxLength(50)]
    public string NotificationType { get; set; } = string.Empty;

    [Required]
    public NotificationCategory Category { get; set; } = NotificationCategory.RIDE;

    [Required]
    [MaxLength(255)]
    public string Title { get; set; } = string.Empty;

    [Required]
    public string Body { get; set; } = string.Empty;

    [MaxLength(500)]
    public string? DeepLink { get; set; }

    [MaxLength(50)]
    public string? RelatedEntityType { get; set; }

    [MaxLength(36)]
    public string? RelatedEntityUuid { get; set; }

    public bool IsRead { get; set; } = false;

    public DateTime? ReadAt { get; set; }

    public NotificationStatus Status { get; set; } = NotificationStatus.CREATED;

    [MaxLength(100)]
    public string? SourceEventId { get; set; }

    [MaxLength(100)]
    public string? IdempotencyKey { get; set; }

    public DateTime? ExpiresAt { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}

[Table("notification_deliveries")]
public class NotificationDelivery
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    public long Id { get; set; }

    [Required]
    [MaxLength(36)]
    public string DeliveryUuid { get; set; } = Guid.NewGuid().ToString();

    [Required]
    [MaxLength(36)]
    public string NotificationUuid { get; set; } = string.Empty;

    [Required]
    public NotificationChannel Channel { get; set; }

    [Required]
    [MaxLength(50)]
    public string Provider { get; set; } = string.Empty;

    [Required]
    [MaxLength(255)]
    public string RecipientDestination { get; set; } = string.Empty;

    public int AttemptNumber { get; set; } = 1;

    [MaxLength(100)]
    public string? ProviderMessageId { get; set; }

    public DeliveryStatus Status { get; set; } = DeliveryStatus.PENDING;

    [MaxLength(50)]
    public string? ErrorCode { get; set; }

    [MaxLength(500)]
    public string? ErrorMessage { get; set; }

    public DateTime? NextRetryAt { get; set; }

    public DateTime AttemptedAt { get; set; } = DateTime.UtcNow;

    public DateTime? CompletedAt { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}

[Table("notification_devices")]
public class NotificationDevice
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    public long Id { get; set; }

    [Required]
    [MaxLength(36)]
    public string DeviceUuid { get; set; } = Guid.NewGuid().ToString();

    [Required]
    [MaxLength(36)]
    public string UserUuid { get; set; } = string.Empty;

    [Required]
    public DevicePlatform Platform { get; set; }

    [Required]
    [MaxLength(500)]
    public string FcmToken { get; set; } = string.Empty;

    [MaxLength(100)]
    public string? DeviceModel { get; set; }

    [MaxLength(50)]
    public string? OsVersion { get; set; }

    [MaxLength(50)]
    public string? AppVersion { get; set; }

    public bool IsActive { get; set; } = true;

    public DateTime LastActiveAt { get; set; } = DateTime.UtcNow;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}

[Table("notification_preferences")]
public class NotificationPreference
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    public long Id { get; set; }

    [Required]
    [MaxLength(36)]
    public string PreferenceUuid { get; set; } = Guid.NewGuid().ToString();

    [Required]
    [MaxLength(36)]
    public string UserUuid { get; set; } = string.Empty;

    public bool PushEnabled { get; set; } = true;

    public bool EmailEnabled { get; set; } = true;

    public bool SmsEnabled { get; set; } = true;

    public bool RideUpdatesEnabled { get; set; } = true;

    public bool PaymentUpdatesEnabled { get; set; } = true;

    public bool PromotionalEnabled { get; set; } = false;

    // Security alerts cannot be disabled
    public bool SecurityAlertsEnabled { get; set; } = true;

    public bool QuietHoursEnabled { get; set; } = false;

    public TimeSpan? QuietHoursStart { get; set; } = new TimeSpan(22, 0, 0);

    public TimeSpan? QuietHoursEnd { get; set; } = new TimeSpan(7, 0, 0);

    [MaxLength(10)]
    public string PreferredLocale { get; set; } = "en";

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}

[Table("notification_event_inbox")]
public class NotificationEventInbox
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    public long Id { get; set; }

    [Required]
    [MaxLength(36)]
    public string EventUuid { get; set; } = Guid.NewGuid().ToString();

    [Required]
    [MaxLength(100)]
    public string EventId { get; set; } = string.Empty;

    [Required]
    [MaxLength(50)]
    public string EventType { get; set; } = string.Empty;

    [Required]
    [MaxLength(50)]
    public string SourceService { get; set; } = string.Empty;

    [Required]
    public string Payload { get; set; } = string.Empty;

    public EventInboxStatus Status { get; set; } = EventInboxStatus.RECEIVED;

    public int RetryCount { get; set; } = 0;

    [MaxLength(1000)]
    public string? ErrorMessage { get; set; }

    public DateTime ReceivedAt { get; set; } = DateTime.UtcNow;

    public DateTime? ProcessedAt { get; set; }
}

[Table("notification_templates")]
public class NotificationTemplate
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    public long Id { get; set; }

    [Required]
    [MaxLength(100)]
    public string TemplateCode { get; set; } = string.Empty;

    [Required]
    public NotificationChannel Channel { get; set; }

    [Required]
    [MaxLength(10)]
    public string Locale { get; set; } = "en";

    [Required]
    [MaxLength(255)]
    public string TitleTemplate { get; set; } = string.Empty;

    [Required]
    public string BodyTemplate { get; set; } = string.Empty;

    public bool IsActive { get; set; } = true;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
