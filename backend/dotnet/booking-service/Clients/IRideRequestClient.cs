using BookingService.Clients.DTOs;

namespace BookingService.Clients;

public interface IRideRequestClient
{
    Task<RideRequestClientDto?> GetRideRequestByUuidAsync(string requestUuid, string? bearerToken = null);
}
