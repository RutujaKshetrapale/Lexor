using System.Net;
using System.Net.Http.Headers;
using System.Text.Json;
using Microsoft.Extensions.Options;
using RideRequestService.Configuration;
using RideRequestService.DTOs.Response;
using RideRequestService.Exceptions;

namespace RideRequestService.Services;

public class RideTypeClient : IRideTypeClient
{
    private readonly HttpClient _httpClient;
    private readonly ILogger<RideTypeClient> _logger;
    private readonly ServiceUrls _serviceUrls;

    public RideTypeClient(HttpClient httpClient, IOptions<ServiceUrls> serviceUrls, ILogger<RideTypeClient> logger)
    {
        _httpClient = httpClient;
        _logger = logger;
        _serviceUrls = serviceUrls.Value;
    }

    public async Task<RideTypeDto?> GetRideTypeByUuidAsync(string rideTypeUuid, string? bearerToken = null)
    {
        if (string.IsNullOrWhiteSpace(rideTypeUuid))
        {
            return null;
        }

        var baseUrl = _serviceUrls.RideTypeServiceUrl?.TrimEnd('/') ?? "http://localhost:8089";
        var url = $"{baseUrl}/api/v1/ride-types/{Uri.EscapeDataString(rideTypeUuid.Trim())}";
        _logger.LogDebug("Calling Ride Type Service at: {Url}", url);

        try
        {
            using var request = new HttpRequestMessage(HttpMethod.Get, url);
            if (!string.IsNullOrWhiteSpace(bearerToken))
            {
                var token = bearerToken.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase)
                    ? bearerToken[7..].Trim()
                    : bearerToken.Trim();
                request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
            }

            using var response = await _httpClient.SendAsync(request);

            if (response.StatusCode == HttpStatusCode.NotFound)
            {
                throw new ResourceNotFoundException($"Ride category with UUID {rideTypeUuid} was not found or is currently disabled.");
            }

            if ((int)response.StatusCode >= 400 && (int)response.StatusCode < 500)
            {
                throw new BadRequestException($"Ride Type Service rejected query with status {response.StatusCode}");
            }

            if ((int)response.StatusCode >= 500)
            {
                throw new ServiceUnavailableException($"Ride Type Service returned internal error: {response.StatusCode}");
            }

            var json = await response.Content.ReadAsStringAsync();
            var options = new JsonSerializerOptions { PropertyNameCaseInsensitive = true };
            var dto = JsonSerializer.Deserialize<RideTypeDto>(json, options);

            if (dto != null && !dto.IsActive)
            {
                throw new BadRequestException($"Ride category '{dto.Name}' is currently disabled and unavailable for booking.");
            }

            return dto;
        }
        catch (ResourceNotFoundException)
        {
            throw;
        }
        catch (BadRequestException)
        {
            throw;
        }
        catch (ServiceUnavailableException)
        {
            throw;
        }
        catch (HttpRequestException ex)
        {
            _logger.LogWarning("Ride Type Service at {Url} is unreachable or timed out: {Message}", url, ex.Message);
            throw new ServiceUnavailableException("Unable to contact Ride Type Service. Please verify service availability.");
        }
        catch (TaskCanceledException ex)
        {
            _logger.LogWarning("Ride Type Service request timed out: {Message}", ex.Message);
            throw new ServiceUnavailableException("Unable to contact Ride Type Service. Request timed out.");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Unexpected error querying Ride Type Service at {Url}: {Message}", url, ex.Message);
            throw new ServiceUnavailableException($"Unexpected error communicating with Ride Type Service: {ex.Message}");
        }
    }
}
