using System.ComponentModel.DataAnnotations;
using NotificationService.Domain.Enums;

namespace NotificationService.DTOs;

public record DeviceRegistrationRequest(
    [Required] DevicePlatform Platform,
    [Required] string FcmToken,
    string? DeviceModel,
    string? OsVersion,
    string? AppVersion
);

public record UpdatePreferencesRequest(
    bool? PushEnabled,
    bool? EmailEnabled,
    bool? SmsEnabled,
    bool? RideUpdatesEnabled,
    bool? PaymentUpdatesEnabled,
    bool? PromotionalEnabled,
    bool? QuietHoursEnabled,
    TimeSpan? QuietHoursStart,
    TimeSpan? QuietHoursEnd,
    string? PreferredLocale
);

public record BusinessEventRequest(
    [Required] string EventId,
    [Required] string EventType,
    [Required] string SourceService,
    string? RecipientUuid,
    string? RecipientRole,
    string? RelatedEntityType,
    string? RelatedEntityUuid,
    Dictionary<string, object>? Metadata
);

public record DirectNotificationRequest(
    [Required] string RecipientUuid,
    [Required] RecipientRole RecipientRole,
    [Required] string NotificationType,
    [Required] NotificationCategory Category,
    [Required] string Title,
    [Required] string Body,
    string? DeepLink,
    string? RelatedEntityType,
    string? RelatedEntityUuid,
    string? IdempotencyKey,
    List<NotificationChannel>? Channels
);

public record OtpDeliveryRequest(
    string? RecipientPhone,
    string? RecipientEmail,
    [Required] string OtpCode,
    [Required] string Purpose,
    int ExpirySeconds = 300
)
{
    public override string ToString() =>
        $"OtpDeliveryRequest(recipientPhone={Mask(RecipientPhone)}, recipientEmail={Mask(RecipientEmail)}, otpCode=[REDACTED], purpose={Purpose})";

    private static string Mask(string? str) =>
        string.IsNullOrEmpty(str) ? "***" : str.Substring(0, Math.Min(3, str.Length)) + "****";
}
