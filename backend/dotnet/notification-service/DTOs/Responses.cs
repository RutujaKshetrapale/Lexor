using NotificationService.Domain.Enums;

namespace NotificationService.DTOs;

public record NotificationDto(
    string NotificationUuid,
    string RecipientUuid,
    RecipientRole RecipientRole,
    string NotificationType,
    NotificationCategory Category,
    string Title,
    string Body,
    string? DeepLink,
    string? RelatedEntityType,
    string? RelatedEntityUuid,
    bool IsRead,
    DateTime? ReadAt,
    NotificationStatus Status,
    DateTime CreatedAt
);

public record UnreadCountResponse(
    string UserUuid,
    long UnreadCount
);

public record DeviceDto(
    string DeviceUuid,
    string UserUuid,
    DevicePlatform Platform,
    string MaskedToken,
    string? DeviceModel,
    string? OsVersion,
    string? AppVersion,
    bool IsActive,
    DateTime LastActiveAt,
    DateTime CreatedAt
);

public record NotificationPreferenceDto(
    string PreferenceUuid,
    string UserUuid,
    bool PushEnabled,
    bool EmailEnabled,
    bool SmsEnabled,
    bool RideUpdatesEnabled,
    bool PaymentUpdatesEnabled,
    bool PromotionalEnabled,
    bool SecurityAlertsEnabled,
    bool QuietHoursEnabled,
    TimeSpan? QuietHoursStart,
    TimeSpan? QuietHoursEnd,
    string PreferredLocale
);

public record DeliveryAuditDto(
    string DeliveryUuid,
    string NotificationUuid,
    NotificationChannel Channel,
    string Provider,
    string MaskedDestination,
    int AttemptNumber,
    string? ProviderMessageId,
    DeliveryStatus Status,
    string? ErrorCode,
    string? ErrorMessage,
    DateTime AttemptedAt,
    DateTime? CompletedAt
);

public record NotificationMetricsResponse(
    long TotalNotifications,
    long TotalDeliveries,
    long DeliveredCount,
    long FailedCount,
    long PendingCount,
    long ActiveDevices,
    long DeadLetterEvents,
    Dictionary<string, long> ChannelBreakdown
);

public record PageResponse<T>(
    List<T> Content,
    int PageNumber,
    int PageSize,
    long TotalElements,
    int TotalPages,
    bool Last
);

public record ApiResponse<T>(
    bool Success,
    string Message,
    T? Data,
    DateTime Timestamp
)
{
    public static ApiResponse<T> Ok(T data, string message = "Operation successful") =>
        new(true, message, data, DateTime.UtcNow);
}

public record ErrorResponse(
    DateTime Timestamp,
    int Status,
    string Error,
    string ErrorCode,
    string Message,
    string Path,
    string CorrelationId
);
