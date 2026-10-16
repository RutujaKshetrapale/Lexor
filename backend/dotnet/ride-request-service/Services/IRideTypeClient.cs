using RideRequestService.DTOs.Response;

namespace RideRequestService.Services;

public interface IRideTypeClient
{
    Task<RideTypeDto?> GetRideTypeByUuidAsync(string rideTypeUuid, string? bearerToken = null);
}
