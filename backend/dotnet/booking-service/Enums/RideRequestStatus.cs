namespace BookingService.Enums;

public enum RideRequestStatus
{
    REQUESTED,
    SEARCHING,
    MATCHED,
    CANCELLED,
    EXPIRED
}

public static class RideRequestStatusExtensions
{
    public static bool IsTerminal(this RideRequestStatus status) =>
        status is RideRequestStatus.CANCELLED or RideRequestStatus.EXPIRED;
}
