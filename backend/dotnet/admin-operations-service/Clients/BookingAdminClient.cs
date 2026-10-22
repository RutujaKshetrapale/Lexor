using System.Text.Json;
using AdminOperationsService.DTOs;

namespace AdminOperationsService.Clients;

public record BookingMetricsResult(
    long TotalRides,
    long CompletedRides,
    long CancelledRides,
    long ActiveRides,
    decimal GrossBookingValue,
    bool IsAvailable,
    string? ErrorMessage
);

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

    private string GetInternalApiKey()
    {
        return Environment.GetEnvironmentVariable("INTERNAL_SERVICE_API_KEY")
            ?? Environment.GetEnvironmentVariable("LEXOR_INTERNAL_SERVICE_KEY")
            ?? _config["InternalService:ApiKey"]
            ?? string.Empty;
    }

    public async Task<BookingMetricsResult> GetBookingMetricsAsync(DateTime start, DateTime end)
    {
        var baseUrl = _config["Services:BookingServiceUrl"] ?? "http://localhost:8096";
        var apiKey = GetInternalApiKey();

        var seenBookingUuids = new HashSet<string>();
        int page = 0;
        int pageSize = 100;
        int maxPages = 50; // Safeguard against unbounded pagination
        long totalCount = 0;
        long completed = 0;
        long cancelled = 0;
        long active = 0;
        decimal gross = 0;

        try
        {
            while (page < maxPages)
            {
                using var request = new HttpRequestMessage(HttpMethod.Get, $"{baseUrl}/api/v1/admin/bookings?page={page}&size={pageSize}");
                if (!string.IsNullOrWhiteSpace(apiKey))
                {
                    request.Headers.Add("X-Internal-Service-Key", apiKey);
                }

                using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(5));
                var response = await _httpClient.SendAsync(request, cts.Token);
                if (!response.IsSuccessStatusCode)
                {
                    _logger.LogWarning("Booking service returned HTTP {StatusCode} on page {Page}", response.StatusCode, page);
                    if (page == 0)
                    {
                        return new BookingMetricsResult(0, 0, 0, 0, 0, false, $"HTTP {(int)response.StatusCode}");
                    }
                    // Partial data scenario on subsequent page
                    return new BookingMetricsResult(totalCount, completed, cancelled, active, gross, false, $"Interrupted at page {page} with HTTP {(int)response.StatusCode}");
                }

                var json = await response.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(json);
                var root = doc.RootElement;

                if (page == 0 && root.TryGetProperty("totalElements", out var te))
                {
                    totalCount = te.GetInt64();
                }

                int totalPages = root.TryGetProperty("totalPages", out var tp) ? tp.GetInt32() : 1;
                bool isLast = root.TryGetProperty("isLast", out var il) && il.GetBoolean();
                if (!isLast && root.TryGetProperty("last", out var l))
                {
                    isLast = l.GetBoolean();
                }

                if (!root.TryGetProperty("content", out var content) || content.ValueKind != JsonValueKind.Array || content.GetArrayLength() == 0)
                {
                    break;
                }

                foreach (var item in content.EnumerateArray())
                {
                    var uuid = item.TryGetProperty("bookingUuid", out var bId) ? bId.GetString() : null;
                    if (!string.IsNullOrWhiteSpace(uuid) && !seenBookingUuids.Add(uuid))
                    {
                        // Duplicate record detected across pages/retries — skip to prevent double counting
                        continue;
                    }

                    var status = item.TryGetProperty("status", out var st) ? st.GetString() : "";
                    if (status == "COMPLETED")
                    {
                        completed++;
                        // Only terminal completed bookings represent confirmed collected gross value
                        if (item.TryGetProperty("fareAmount", out var fare) && fare.TryGetDecimal(out var fVal)) gross += fVal;
                        else if (item.TryGetProperty("totalFare", out var tf) && tf.TryGetDecimal(out var tfVal)) gross += tfVal;
                    }
                    else if (status != null && (status.StartsWith("CANCELLED") || status == "DECLINED_BY_DRIVER" || status == "EXPIRED"))
                    {
                        cancelled++;
                    }
                    else if (status == "IN_PROGRESS" || status == "ACCEPTED" || status == "ARRIVED" || status == "CONFIRMED")
                    {
                        active++;
                    }
                }

                page++;
                if (isLast || page >= totalPages)
                {
                    break;
                }
            }

            long authoritativeTotal = totalCount > 0 ? totalCount : seenBookingUuids.Count;
            return new BookingMetricsResult(authoritativeTotal, completed, cancelled, active, gross, true, null);
        }
        catch (Exception ex)
        {
            _logger.LogWarning("Failed to query booking metrics: {Message}", ex.Message);
            return new BookingMetricsResult(0, 0, 0, 0, 0, false, ex.Message);
        }
    }

    public async Task<List<ActiveRideItem>> GetActiveRidesAsync()
    {
        var items = new List<ActiveRideItem>();
        try
        {
            var baseUrl = _config["Services:BookingServiceUrl"] ?? "http://localhost:8096";
            var apiKey = GetInternalApiKey();

            using var request = new HttpRequestMessage(HttpMethod.Get, $"{baseUrl}/api/v1/admin/bookings?status=IN_PROGRESS&size=20");
            if (!string.IsNullOrWhiteSpace(apiKey))
            {
                request.Headers.Add("X-Internal-Service-Key", apiKey);
            }

            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(5));
            var response = await _httpClient.SendAsync(request, cts.Token);
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
            var apiKey = GetInternalApiKey();

            using var request = new HttpRequestMessage(HttpMethod.Get, $"{baseUrl}/api/v1/admin/bookings?size=15");
            if (!string.IsNullOrWhiteSpace(apiKey))
            {
                request.Headers.Add("X-Internal-Service-Key", apiKey);
            }

            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(5));
            var response = await _httpClient.SendAsync(request, cts.Token);
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
