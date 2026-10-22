using System.Collections.Concurrent;
using System.Text.Json;
using FeedbackService.Configuration;
using FeedbackService.DTOs;
using Microsoft.Extensions.Options;

namespace FeedbackService.Clients;

public class BookingVerificationClient : IBookingVerificationClient
{
    private readonly HttpClient _httpClient;
    private readonly FeedbackSettings _settings;
    private readonly ILogger<BookingVerificationClient> _logger;
    private readonly ConcurrentDictionary<string, RideEligibilityDto> _mockRegistry = new();

    public BookingVerificationClient(
        HttpClient httpClient,
        IOptions<FeedbackSettings> settings,
        ILogger<BookingVerificationClient> logger)
    {
        _httpClient = httpClient;
        _settings = settings.Value;
        _logger = logger;
    }

    public async Task<RideEligibilityDto?> VerifyRideEligibilityAsync(string rideOrBookingUuid, string? authHeader = null)
    {
        if (string.IsNullOrWhiteSpace(rideOrBookingUuid)) return null;

        // 1. Check in-memory test registry first
        if (_mockRegistry.TryGetValue(rideOrBookingUuid, out var mockDto))
        {
            return mockDto;
        }

        // 2. Perform HTTP RPC call to Booking Service
        try
        {
            var url = $"{_settings.BookingServiceUrl}/api/v1/admin/bookings/{rideOrBookingUuid}";
            using var request = new HttpRequestMessage(HttpMethod.Get, url);
            request.Headers.Add("X-Internal-Service-Key", _settings.InternalServiceKey);
            if (!string.IsNullOrWhiteSpace(authHeader))
            {
                request.Headers.Add("Authorization", authHeader);
            }

            var response = await _httpClient.SendAsync(request);
            if (response.IsSuccessStatusCode)
            {
                var content = await response.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(content);
                var root = doc.RootElement;

                var bookingUuid = root.TryGetProperty("bookingUuid", out var bu) ? bu.GetString() ?? rideOrBookingUuid : rideOrBookingUuid;
                var status = root.TryGetProperty("status", out var st) ? st.GetString() ?? "CONFIRMED" : "CONFIRMED";

                string riderUuid = string.Empty;
                string riderName = string.Empty;
                if (root.TryGetProperty("rider", out var riderElem))
                {
                    if (riderElem.TryGetProperty("riderUuid", out var ru)) riderUuid = ru.GetString() ?? string.Empty;
                    var fn = riderElem.TryGetProperty("firstName", out var rfn) ? rfn.GetString() : string.Empty;
                    var ln = riderElem.TryGetProperty("lastName", out var rln) ? rln.GetString() : string.Empty;
                    riderName = $"{fn} {ln}".Trim();
                }

                string driverUuid = string.Empty;
                string driverName = string.Empty;
                if (root.TryGetProperty("driver", out var driverElem))
                {
                    if (driverElem.TryGetProperty("driverUuid", out var du)) driverUuid = du.GetString() ?? string.Empty;
                    var dfn = driverElem.TryGetProperty("firstName", out var dfnElem) ? dfnElem.GetString() : string.Empty;
                    var dln = driverElem.TryGetProperty("lastName", out var dlnElem) ? dlnElem.GetString() : string.Empty;
                    driverName = $"{dfn} {dln}".Trim();
                }

                var completedAt = DateTime.UtcNow;
                if (root.TryGetProperty("completedAt", out var compElem) && compElem.ValueKind == JsonValueKind.String)
                {
                    if (DateTime.TryParse(compElem.GetString(), out var parsed))
                    {
                        completedAt = parsed;
                    }
                }

                return new RideEligibilityDto
                {
                    RideUuid = bookingUuid,
                    BookingUuid = bookingUuid,
                    Status = status,
                    RiderUuid = riderUuid,
                    DriverUuid = driverUuid,
                    RiderName = riderName,
                    DriverName = driverName,
                    CompletedAt = completedAt
                };
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning("Could not verify ride eligibility from Booking Service at {Url}: {Message}",
                _settings.BookingServiceUrl, ex.Message);
        }

        return null;
    }

    public void RegisterMockRide(RideEligibilityDto mockRide)
    {
        if (mockRide != null)
        {
            if (!string.IsNullOrEmpty(mockRide.RideUuid)) _mockRegistry[mockRide.RideUuid] = mockRide;
            if (!string.IsNullOrEmpty(mockRide.BookingUuid)) _mockRegistry[mockRide.BookingUuid] = mockRide;
        }
    }

    public void ClearMockRides()
    {
        _mockRegistry.Clear();
    }
}
