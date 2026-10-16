namespace RideRequestService.Enums;

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
    public static bool CanCancel(this RideRequestStatus status)
    {
        return status is RideRequestStatus.REQUESTED or RideRequestStatus.SEARCHING;
    }

    public static bool IsTerminal(this RideRequestStatus status)
    {
        return status is RideRequestStatus.MATCHED or RideRequestStatus.CANCELLED or RideRequestStatus.EXPIRED;
    }

    public static bool CanTransitionTo(this RideRequestStatus current, RideRequestStatus target)
    {
        if (current == target) return false;

        return current switch
        {
            RideRequestStatus.REQUESTED => target is RideRequestStatus.SEARCHING or RideRequestStatus.CANCELLED or RideRequestStatus.EXPIRED,
            RideRequestStatus.SEARCHING => target is RideRequestStatus.MATCHED or RideRequestStatus.CANCELLED or RideRequestStatus.EXPIRED,
            RideRequestStatus.MATCHED or RideRequestStatus.CANCELLED or RideRequestStatus.EXPIRED => false,
            _ => false
        };
    }
}
