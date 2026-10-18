using System.Net;
using System.Net.Http.Headers;
using System.Text.Json;
using BookingService.Clients.DTOs;
using BookingService.Configuration;
using BookingService.Exceptions;
using Microsoft.Extensions.Options;

namespace BookingService.Clients;

public class RideRequestClient : IRideRequestClient
{
    private readonly HttpClient _httpClient;
    private readonly ServiceUrls _serviceUrls;
    private readonly ILogger<RideRequestClient> _logger;
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNameCaseInsensitive = true
    };

    public RideRequestClient(
        HttpClient httpClient,
        IOptions<ServiceUrls> serviceUrls,
        ILogger<RideRequestClient> logger)
    {
        _httpClient = httpClient;
        _serviceUrls = serviceUrls.Value;
        _logger = logger;
    }

    public async Task<RideRequestClientDto?> GetRideRequestByUuidAsync(string requestUuid, string? bearerToken = null)
    {
        if (string.IsNullOrWhiteSpace(requestUuid))
        {
            return null;
        }

        var baseUrl = _serviceUrls.RideRequestServiceUrl.TrimEnd('/');
        var url = $"{baseUrl}/api/v1/ride-requests/me/{requestUuid.Trim()}";
        _logger.LogDebug("Calling Ride Request Service at: {Url}", url);

        try
        {
            using var request = new HttpRequestMessage(HttpMethod.Get, url);
            if (!string.IsNullOrWhiteSpace(bearerToken))
            {
                var token = bearerToken.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase)
                    ? bearerToken["Bearer ".Length..].Trim()
                    : bearerToken.Trim();
                request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
            }

            var response = await _httpClient.SendAsync(request);

            if (response.IsSuccessStatusCode)
            {
                var content = await response.Content.ReadAsStringAsync();
                return JsonSerializer.Deserialize<RideRequestClientDto>(content, JsonOptions);
            }

            var statusCode = (int)response.StatusCode;
            if (response.StatusCode == HttpStatusCode.NotFound)
            {
                throw new ResourceNotFoundException($"Ride request not found with UUID: {requestUuid}");
            }
            if (response.StatusCode == HttpStatusCode.Forbidden)
            {
                throw new ForbiddenException($"Access denied: You do not own ride request {requestUuid}");
            }
            if ((int)response.StatusCode >= 400 && (int)response.StatusCode < 500)
            {
                throw new BadRequestException($"Ride Request Service rejected request with status: {statusCode}");
            }
            if ((int)response.StatusCode >= 500)
            {
                throw new ServiceUnavailableException($"Ride Request Service returned internal error: {statusCode}");
            }

            return null;
        }
        catch (Exception ex) when (ex is ResourceNotFoundException or ForbiddenException or BadRequestException or ServiceUnavailableException)
        {
            throw;
        }
        catch (HttpRequestException ex)
        {
            _logger.LogError(ex, "Timeout or connection failure contacting Ride Request Service: {Message}", ex.Message);
            throw new ServiceUnavailableException("Ride Request Service is currently unreachable or timed out.");
        }
        catch (TaskCanceledException ex)
        {
            _logger.LogError(ex, "Request timed out contacting Ride Request Service");
            throw new ServiceUnavailableException("Ride Request Service timed out.");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Unexpected error contacting Ride Request Service: {Message}", ex.Message);
            throw new ServiceUnavailableException($"Failed to communicate with Ride Request Service: {ex.Message}");
        }
    }
}
