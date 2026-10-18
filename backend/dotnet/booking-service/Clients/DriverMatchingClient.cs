using System.Net;
using System.Text.Json;
using BookingService.Clients.DTOs;
using BookingService.Configuration;
using BookingService.Exceptions;
using Microsoft.Extensions.Options;

namespace BookingService.Clients;

public class DriverMatchingClient : IDriverMatchingClient
{
    private readonly HttpClient _httpClient;
    private readonly ServiceUrls _serviceUrls;
    private readonly BookingSettings _bookingSettings;
    private readonly ILogger<DriverMatchingClient> _logger;
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNameCaseInsensitive = true
    };

    public DriverMatchingClient(
        HttpClient httpClient,
        IOptions<ServiceUrls> serviceUrls,
        IOptions<BookingSettings> bookingSettings,
        ILogger<DriverMatchingClient> logger)
    {
        _httpClient = httpClient;
        _serviceUrls = serviceUrls.Value;
        _bookingSettings = bookingSettings.Value;
        _logger = logger;
    }

    public async Task<MatchingAttemptClientDto?> GetMatchingStatusAsync(string requestUuid)
    {
        if (string.IsNullOrWhiteSpace(requestUuid))
        {
            return null;
        }

        var baseUrl = _serviceUrls.MatchingServiceUrl.TrimEnd('/');
        var url = $"{baseUrl}/api/v1/internal/matching/ride-requests/{requestUuid.Trim()}";
        _logger.LogDebug("Calling Driver Matching Service status at: {Url}", url);

        try
        {
            using var request = new HttpRequestMessage(HttpMethod.Get, url);
            request.Headers.Add("X-Internal-Service-Key", _bookingSettings.InternalApiKey);

            var response = await _httpClient.SendAsync(request);

            if (response.IsSuccessStatusCode)
            {
                var content = await response.Content.ReadAsStringAsync();
                return JsonSerializer.Deserialize<MatchingAttemptClientDto>(content, JsonOptions);
            }

            var statusCode = (int)response.StatusCode;
            if (response.StatusCode == HttpStatusCode.NotFound)
            {
                throw new ResourceNotFoundException($"No matching attempt found for ride request: {requestUuid}");
            }
            if (response.StatusCode == HttpStatusCode.Conflict)
            {
                throw new ConflictException($"Matching status conflict for ride request: {requestUuid}");
            }
            if ((int)response.StatusCode >= 400 && (int)response.StatusCode < 500)
            {
                throw new BadRequestException($"Driver Matching Service rejected query with status: {statusCode}");
            }
            if ((int)response.StatusCode >= 500)
            {
                throw new ServiceUnavailableException($"Driver Matching Service returned internal error: {statusCode}");
            }

            return null;
        }
        catch (Exception ex) when (ex is ResourceNotFoundException or ConflictException or BadRequestException or ServiceUnavailableException)
        {
            throw;
        }
        catch (HttpRequestException ex)
        {
            _logger.LogError(ex, "Timeout or connection failure contacting Driver Matching Service: {Message}", ex.Message);
            throw new ServiceUnavailableException("Driver Matching Service is currently unreachable or timed out.");
        }
        catch (TaskCanceledException ex)
        {
            _logger.LogError(ex, "Request timed out contacting Driver Matching Service");
            throw new ServiceUnavailableException("Driver Matching Service timed out.");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Unexpected error contacting Driver Matching Service: {Message}", ex.Message);
            throw new ServiceUnavailableException($"Failed to communicate with Driver Matching Service: {ex.Message}");
        }
    }

    public async Task<MatchingAttemptClientDto?> InitiateMatchingAsync(string requestUuid, string? correlationId = null)
    {
        if (string.IsNullOrWhiteSpace(requestUuid))
        {
            return null;
        }

        var baseUrl = _serviceUrls.MatchingServiceUrl.TrimEnd('/');
        var url = $"{baseUrl}/api/v1/internal/matching/ride-requests/{requestUuid.Trim()}/attempts";
        _logger.LogDebug("Initiating matching via Driver Matching Service at: {Url}", url);

        try
        {
            using var request = new HttpRequestMessage(HttpMethod.Post, url);
            request.Headers.Add("X-Internal-Service-Key", _bookingSettings.InternalApiKey);
            if (!string.IsNullOrWhiteSpace(correlationId))
            {
                request.Headers.Add("X-Correlation-Id", correlationId);
            }

            var response = await _httpClient.SendAsync(request);

            if (response.IsSuccessStatusCode)
            {
                var content = await response.Content.ReadAsStringAsync();
                return JsonSerializer.Deserialize<MatchingAttemptClientDto>(content, JsonOptions);
            }

            var statusCode = (int)response.StatusCode;
            if (response.StatusCode == HttpStatusCode.NotFound)
            {
                throw new ResourceNotFoundException($"Ride request not found for matching: {requestUuid}");
            }
            if (response.StatusCode == HttpStatusCode.Conflict)
            {
                throw new ConflictException($"Ride request cannot be matched in current state: {requestUuid}");
            }
            if ((int)response.StatusCode >= 400 && (int)response.StatusCode < 500)
            {
                throw new BadRequestException($"Driver Matching Service rejected initiation with status: {statusCode}");
            }
            if ((int)response.StatusCode >= 500)
            {
                throw new ServiceUnavailableException($"Driver Matching Service returned internal error: {statusCode}");
            }

            return null;
        }
        catch (Exception ex) when (ex is ResourceNotFoundException or ConflictException or BadRequestException or ServiceUnavailableException)
        {
            throw;
        }
        catch (HttpRequestException ex)
        {
            _logger.LogError(ex, "Timeout or connection failure initiating matching: {Message}", ex.Message);
            throw new ServiceUnavailableException("Driver Matching Service is currently unreachable or timed out.");
        }
        catch (TaskCanceledException ex)
        {
            _logger.LogError(ex, "Request timed out initiating matching");
            throw new ServiceUnavailableException("Driver Matching Service timed out.");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Unexpected error initiating matching: {Message}", ex.Message);
            throw new ServiceUnavailableException($"Failed to communicate with Driver Matching Service: {ex.Message}");
        }
    }
}
