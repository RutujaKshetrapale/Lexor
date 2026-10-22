using FeedbackService.DTOs;

namespace FeedbackService.Clients;

public interface IBookingVerificationClient
{
    Task<RideEligibilityDto?> VerifyRideEligibilityAsync(string rideOrBookingUuid, string? authHeader = null);
    void RegisterMockRide(RideEligibilityDto mockRide);
    void ClearMockRides();
}
