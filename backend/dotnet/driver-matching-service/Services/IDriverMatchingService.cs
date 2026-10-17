using DriverMatchingService.DTOs.Request;
using DriverMatchingService.DTOs.Response;

namespace DriverMatchingService.Services;

public interface IDriverMatchingService
{
    Task<MatchingAttemptResponse> InitiateMatchingAsync(string requestUuid, string? correlationId);

    Task<MatchingAttemptResponse> GetMatchingStatusAsync(string requestUuid);

    Task<MatchingAttemptResponse> RetryMatchingAsync(string requestUuid, MatchingRetryRequestDto retryRequest);
}
