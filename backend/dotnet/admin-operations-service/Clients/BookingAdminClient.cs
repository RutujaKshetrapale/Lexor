using System.Text.Json;
using AdminOperationsService.DTOs;

namespace AdminOperationsService.Clients;

public class BookingAdminClient
{
    private readonly HttpClient _httpClient;
    private readonly IConfiguration _config;
    private readonly ILogger<BookingAdminClient> _logger;

    public BookingAdminClient(HttpClient httpClient, IConfiguration config, ILogger<BookingAdminClient> logger)
    {
        _httpClient = httpClient;
        _config = config;
        _logger = logger;
    }

    public async Task<(long totalRides, long completedRides, long cancelledRides, long activeRides, decimal grossBookingValue)> GetBookingMetricsAsync(DateTime start, DateTime end)
    {
        try
        {
            var baseUrl = _config["Services:BookingServiceUrl"] ?? "http://localhost:8096";
            var apiKey = _config["InternalService:ApiKey"] ?? "lexor-internal-service-secret-key-2026";

            using var request = new HttpRequestMessage(HttpMethod.Get, $"{baseUrl}/api/v1/admin/bookings?size=100");
            request.Headers.Add("X-Internal-Service-Key", apiKey);

            var response = await _httpClient.SendAsync(request);
            if (response.IsSuccessStatusCode)
            {
                var json = await response.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(json);
                var root = doc.RootElement;

                long total = root.TryGetProperty("totalElements", out var te) ? te.GetInt64() : 0;
                long completed = 0;
                long cancelled = 0;
                long active = 0;
                decimal gross = 0;

                if (root.TryGetProperty("content", out var content) && content.ValueKind == JsonValueKind.Array)
                {
                    foreach (var item in content.EnumerateArray())
                    {
                        var status = item.TryGetProperty("status", out var st) ? st.GetString() : "";
                        if (status == "COMPLETED") completed++;
                        else if (status != null && (status.StartsWith("CANCELLED") || status == "DECLINED_BY_DRIVER" || status == "EXPIRED")) cancelled++;
                        else if (status == "IN_PROGRESS" || status == "ACCEPTED" || status == "ARRIVED" || status == "CONFIRMED") active++;

                        if (item.TryGetProperty("fareAmount", out var fare) && fare.TryGetDecimal(out var fVal)) gross += fVal;
                        else if (item.TryGetProperty("totalFare", out var tf) && tf.TryGetDecimal(out var tfVal)) gross += tfVal;
                    }
                }

                return (total, completed, cancelled, active, gross);
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning("Failed to query booking metrics: {Message}", ex.Message);
        }

        return (0, 0, 0, 0, 0);
    }

    public async Task<List<ActiveRideItem>> GetActiveRidesAsync()
    {
        var items = new List<ActiveRideItem>();
        try
        {
            var baseUrl = _config["Services:BookingServiceUrl"] ?? "http://localhost:8096";
            var apiKey = _config["InternalService:ApiKey"] ?? "lexor-internal-service-secret-key-2026";

            using var request = new HttpRequestMessage(HttpMethod.Get, $"{baseUrl}/api/v1/admin/bookings?status=IN_PROGRESS&size=20");
            request.Headers.Add("X-Internal-Service-Key", apiKey);

            var response = await _httpClient.SendAsync(request);
            if (response.IsSuccessStatusCode)
            {
                var json = await response.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(json);
                if (doc.RootElement.TryGetProperty("content", out var content) && content.ValueKind == JsonValueKind.Array)
                {
                    foreach (var item in content.EnumerateArray())
                    {
                        items.Add(new ActiveRideItem
                        {
                            BookingUuid = item.TryGetProperty("bookingUuid", out var bId) ? bId.GetString() : "",
                            RideUuid = item.TryGetProperty("rideUuid", out var rId) ? rId.GetString() : "",
                            RiderUuid = item.TryGetProperty("riderUuid", out var ruId) ? ruId.GetString() : "",
                            DriverUuid = item.TryGetProperty("driverUuid", out var duId) ? duId.GetString() : "",
                            PickupAddress = item.TryGetProperty("pickupAddress", out var pa) ? pa.GetString() : "",
                            DropoffAddress = item.TryGetProperty("dropoffAddress", out var da) ? da.GetString() : "",
                            Status = item.TryGetProperty("status", out var st) ? st.GetString() : "IN_PROGRESS",
                            EstimatedFare = item.TryGetProperty("fareAmount", out var fa) && fa.TryGetDecimal(out var d) ? d : 0,
                            StartTime = DateTime.UtcNow
                        });
                    }
                }
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning("Failed to query active rides: {Message}", ex.Message);
        }
        return items;
    }

    public async Task<List<RecentCancellationItem>> GetRecentCancellationsAsync()
    {
        var items = new List<RecentCancellationItem>();
        try
        {
            var baseUrl = _config["Services:BookingServiceUrl"] ?? "http://localhost:8096";
            var apiKey = _config["InternalService:ApiKey"] ?? "lexor-internal-service-secret-key-2026";

            using var request = new HttpRequestMessage(HttpMethod.Get, $"{baseUrl}/api/v1/admin/bookings?size=15");
            request.Headers.Add("X-Internal-Service-Key", apiKey);

            var response = await _httpClient.SendAsync(request);
            if (response.IsSuccessStatusCode)
            {
                var json = await response.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(json);
                if (doc.RootElement.TryGetProperty("content", out var content) && content.ValueKind == JsonValueKind.Array)
                {
                    foreach (var item in content.EnumerateArray())
                    {
                        var status = item.TryGetProperty("status", out var st) ? st.GetString() : "";
                        if (status != null && status.StartsWith("CANCELLED"))
                        {
                            items.Add(new RecentCancellationItem
                            {
                                BookingUuid = item.TryGetProperty("bookingUuid", out var bId) ? bId.GetString() : "",
                                CancelledBy = status.Contains("RIDER") ? "RIDER" : status.Contains("DRIVER") ? "DRIVER" : "ADMIN",
                                Reason = item.TryGetProperty("cancellationReason", out var cr) ? cr.GetString() : "Operational cancellation",
                                CancelledAt = DateTime.UtcNow
                            });
                        }
                    }
                }
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning("Failed to query recent cancellations: {Message}", ex.Message);
        }
        return items;
    }
}
