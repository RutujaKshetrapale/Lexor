using BookingService.Clients.DTOs;

namespace BookingService.Clients;

public interface IDriverMatchingClient
{
    Task<MatchingAttemptClientDto?> GetMatchingStatusAsync(string requestUuid);
    Task<MatchingAttemptClientDto?> InitiateMatchingAsync(string requestUuid, string? correlationId = null);
}
