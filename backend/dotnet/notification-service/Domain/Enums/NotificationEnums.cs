namespace NotificationService.Domain.Enums;

public enum NotificationType
{
    RIDE_REQUEST_CREATED,
    DRIVER_MATCHING_STARTED,
    DRIVER_ASSIGNED,
    DRIVER_ARRIVED,
    RIDE_STARTED,
    RIDE_COMPLETED,
    RIDE_CANCELLED,
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,
    REFUND_INITIATED,
    REFUND_COMPLETED,
    DRIVER_OFFER,
    SECURITY_ALERT,
    AUTH_OTP,
    SYSTEM_ALERT,
    GENERAL_PROMOTION
}

public enum NotificationCategory
{
    RIDE,
    PAYMENT,
    SECURITY,
    SYSTEM,
    PROMOTION
}

public enum NotificationStatus
{
    CREATED,
    QUEUED,
    PROCESSING,
    COMPLETED,
    PARTIALLY_FAILED,
    FAILED,
    CANCELLED
}

public enum NotificationChannel
{
    PUSH,
    SMS,
    EMAIL,
    IN_APP
}

public enum DeliveryStatus
{
    PENDING,
    SUBMITTED,
    ACCEPTED,
    DELIVERED,
    FAILED,
    RETRY_SCHEDULED,
    PERMANENTLY_FAILED
}

public enum DevicePlatform
{
    ANDROID,
    IOS,
    WEB
}

public enum EventInboxStatus
{
    RECEIVED,
    PROCESSING,
    PROCESSED,
    FAILED,
    DEAD_LETTER
}

public enum RecipientRole
{
    RIDER,
    DRIVER,
    ADMIN
}
