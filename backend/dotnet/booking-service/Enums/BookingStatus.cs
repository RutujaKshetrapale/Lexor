namespace BookingService.Enums;

public enum BookingStatus
{
    OFFERED,
    DRIVER_ASSIGNED,
    CONFIRMED,
    ACCEPTED,
    ARRIVED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED_BY_RIDER,
    CANCELLED_BY_DRIVER,
    CANCELLED_BY_ADMIN,
    DECLINED_BY_DRIVER,
    EXPIRED
}

public static class BookingStatusExtensions
{
    public static bool IsTerminal(this BookingStatus status) =>
        status is BookingStatus.COMPLETED or
                  BookingStatus.CANCELLED_BY_RIDER or
                  BookingStatus.CANCELLED_BY_DRIVER or
                  BookingStatus.CANCELLED_BY_ADMIN or
                  BookingStatus.DECLINED_BY_DRIVER or
                  BookingStatus.EXPIRED;

    public static bool CanRiderCancel(this BookingStatus status) =>
        status is BookingStatus.OFFERED or
                  BookingStatus.DRIVER_ASSIGNED or
                  BookingStatus.CONFIRMED or
                  BookingStatus.ACCEPTED or
                  BookingStatus.ARRIVED;

    public static bool CanDriverAccept(this BookingStatus status) =>
        status is BookingStatus.OFFERED or
                  BookingStatus.DRIVER_ASSIGNED or
                  BookingStatus.CONFIRMED;

    public static bool CanDriverDecline(this BookingStatus status) =>
        status is BookingStatus.OFFERED or
                  BookingStatus.DRIVER_ASSIGNED or
                  BookingStatus.CONFIRMED;

    public static bool CanDriverArrive(this BookingStatus status) =>
        status is BookingStatus.ACCEPTED or
                  BookingStatus.CONFIRMED;

    public static bool CanDriverStart(this BookingStatus status) =>
        status is BookingStatus.ARRIVED or
                  BookingStatus.ACCEPTED;

    public static bool CanDriverComplete(this BookingStatus status) =>
        status is BookingStatus.IN_PROGRESS;
}
