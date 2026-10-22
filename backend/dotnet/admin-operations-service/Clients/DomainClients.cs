using System.Text.Json;
using AdminOperationsService.DTOs;

namespace AdminOperationsService.Clients;

public class DriverAdminClient
{
    private readonly HttpClient _httpClient;
    private readonly IConfiguration _config;
    private readonly ILogger<DriverAdminClient> _logger;

    public DriverAdminClient(HttpClient httpClient, IConfiguration config, ILogger<DriverAdminClient> logger)
    {
        _httpClient = httpClient;
        _config = config;
        _logger = logger;
    }

    public async Task<(long totalDrivers, long onlineDrivers, long inTripDrivers, long pendingVerifications)> GetDriverMetricsAsync()
    {
        try
        {
            var baseUrl = _config["Services:DriverServiceUrl"] ?? "http://localhost:8086";
            var apiKey = _config["InternalService:ApiKey"] ?? "lexor-internal-service-secret-key-2026";

            using var request = new HttpRequestMessage(HttpMethod.Get, $"{baseUrl}/api/v1/admin/drivers?size=100");
            request.Headers.Add("X-Internal-Service-Key", apiKey);

            var response = await _httpClient.SendAsync(request);
            if (response.IsSuccessStatusCode)
            {
                var json = await response.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(json);
                var root = doc.RootElement;

                long total = root.TryGetProperty("totalElements", out var te) ? te.GetInt64() : 0;
                long online = 0;
                long inTrip = 0;
                long pendingVerif = 0;

                if (root.TryGetProperty("content", out var content) && content.ValueKind == JsonValueKind.Array)
                {
                    foreach (var item in content.EnumerateArray())
                    {
                        var status = item.TryGetProperty("status", out var st) ? st.GetString() : "";
                        var onboarding = item.TryGetProperty("onboardingStatus", out var ob) ? ob.GetString() : "";

                        if (status == "ONLINE") online++;
                        if (status == "IN_TRIP") inTrip++;
                        if (onboarding == "PENDING_VERIFICATION" || onboarding == "UNDER_REVIEW") pendingVerif++;
                    }
                }

                return (total, online, inTrip, pendingVerif);
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning("Failed to query driver metrics: {Message}", ex.Message);
        }

        return (0, 0, 0, 0);
    }
}

public class VehicleAdminClient
{
    private readonly HttpClient _httpClient;
    private readonly IConfiguration _config;
    private readonly ILogger<VehicleAdminClient> _logger;

    public VehicleAdminClient(HttpClient httpClient, IConfiguration config, ILogger<VehicleAdminClient> logger)
    {
        _httpClient = httpClient;
        _config = config;
        _logger = logger;
    }

    public async Task<long> GetRegisteredVehiclesCountAsync()
    {
        try
        {
            var baseUrl = _config["Services:VehicleServiceUrl"] ?? "http://localhost:8088";
            var apiKey = _config["InternalService:ApiKey"] ?? "lexor-internal-service-secret-key-2026";

            using var request = new HttpRequestMessage(HttpMethod.Get, $"{baseUrl}/api/v1/admin/vehicles?size=1");
            request.Headers.Add("X-Internal-Service-Key", apiKey);

            var response = await _httpClient.SendAsync(request);
            if (response.IsSuccessStatusCode)
            {
                var json = await response.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(json);
                if (doc.RootElement.TryGetProperty("totalElements", out var te))
                {
                    return te.GetInt64();
                }
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning("Failed to query vehicle count: {Message}", ex.Message);
        }
        return 0;
    }
}

public class UserAdminClient
{
    private readonly HttpClient _httpClient;
    private readonly IConfiguration _config;
    private readonly ILogger<UserAdminClient> _logger;

    public UserAdminClient(HttpClient httpClient, IConfiguration config, ILogger<UserAdminClient> logger)
    {
        _httpClient = httpClient;
        _config = config;
        _logger = logger;
    }

    public async Task<(long registeredRiders, long registeredDrivers)> GetUserCountsAsync()
    {
        long riders = 0;
        long drivers = 0;
        try
        {
            var baseUrl = _config["Services:UserServiceUrl"] ?? "http://localhost:8084";
            var apiKey = _config["InternalService:ApiKey"] ?? "lexor-internal-service-secret-key-2026";

            using var riderReq = new HttpRequestMessage(HttpMethod.Get, $"{baseUrl}/api/v1/users?role=RIDER&size=1");
            riderReq.Headers.Add("X-Internal-Service-Key", apiKey);
            var riderResp = await _httpClient.SendAsync(riderReq);
            if (riderResp.IsSuccessStatusCode)
            {
                var json = await riderResp.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(json);
                if (doc.RootElement.TryGetProperty("totalElements", out var te)) riders = te.GetInt64();
            }

            using var driverReq = new HttpRequestMessage(HttpMethod.Get, $"{baseUrl}/api/v1/users?role=DRIVER&size=1");
            driverReq.Headers.Add("X-Internal-Service-Key", apiKey);
            var driverResp = await _httpClient.SendAsync(driverReq);
            if (driverResp.IsSuccessStatusCode)
            {
                var json = await driverResp.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(json);
                if (doc.RootElement.TryGetProperty("totalElements", out var te)) drivers = te.GetInt64();
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning("Failed to query user counts: {Message}", ex.Message);
        }
        return (riders, drivers);
    }
}

public class RideRequestAdminClient
{
    private readonly HttpClient _httpClient;
    private readonly IConfiguration _config;
    private readonly ILogger<RideRequestAdminClient> _logger;

    public RideRequestAdminClient(HttpClient httpClient, IConfiguration config, ILogger<RideRequestAdminClient> logger)
    {
        _httpClient = httpClient;
        _config = config;
        _logger = logger;
    }

    public async Task<long> GetPendingRequestsCountAsync()
    {
        try
        {
            var baseUrl = _config["Services:RideRequestServiceUrl"] ?? "http://localhost:8092";
            var apiKey = _config["InternalService:ApiKey"] ?? "lexor-internal-service-secret-key-2026";

            using var request = new HttpRequestMessage(HttpMethod.Get, $"{baseUrl}/api/v1/admin/ride-requests?status=PENDING&size=1");
            request.Headers.Add("X-Internal-Service-Key", apiKey);

            var response = await _httpClient.SendAsync(request);
            if (response.IsSuccessStatusCode)
            {
                var json = await response.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(json);
                if (doc.RootElement.TryGetProperty("totalElements", out var te)) return te.GetInt64();
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning("Failed to query pending ride requests count: {Message}", ex.Message);
        }
        return 0;
    }

    public async Task<List<PendingRequestItem>> GetPendingRequestsAsync()
    {
        var items = new List<PendingRequestItem>();
        try
        {
            var baseUrl = _config["Services:RideRequestServiceUrl"] ?? "http://localhost:8092";
            var apiKey = _config["InternalService:ApiKey"] ?? "lexor-internal-service-secret-key-2026";

            using var request = new HttpRequestMessage(HttpMethod.Get, $"{baseUrl}/api/v1/admin/ride-requests?status=PENDING&size=20");
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
                        items.Add(new PendingRequestItem
                        {
                            RequestUuid = item.TryGetProperty("requestUuid", out var rqId) ? rqId.GetString() : "",
                            RiderUuid = item.TryGetProperty("riderUuid", out var ruId) ? ruId.GetString() : "",
                            RideType = item.TryGetProperty("rideType", out var rt) ? rt.GetString() : "STANDARD",
                            PickupAddress = item.TryGetProperty("pickupAddress", out var pa) ? pa.GetString() : "",
                            DropoffAddress = item.TryGetProperty("dropoffAddress", out var da) ? da.GetString() : "",
                            Status = "PENDING",
                            MatchingAttemptsCount = item.TryGetProperty("matchingAttemptsCount", out var mac) ? mac.GetInt32() : 1,
                            RequestedAt = DateTime.UtcNow.AddMinutes(-2),
                            WaitingSeconds = 120
                        });
                    }
                }
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning("Failed to query pending requests: {Message}", ex.Message);
        }
        return items;
    }
}

public class FeedbackAdminClient
{
    private readonly HttpClient _httpClient;
    private readonly IConfiguration _config;
    private readonly ILogger<FeedbackAdminClient> _logger;

    public FeedbackAdminClient(HttpClient httpClient, IConfiguration config, ILogger<FeedbackAdminClient> logger)
    {
        _httpClient = httpClient;
        _config = config;
        _logger = logger;
    }

    public async Task<(decimal averageRating, long pendingReports, Dictionary<string, long> starDistribution)> GetFeedbackMetricsAsync()
    {
        var dist = new Dictionary<string, long> { ["5"] = 0, ["4"] = 0, ["3"] = 0, ["2"] = 0, ["1"] = 0 };
        decimal avg = 5.0m;
        long reports = 0;

        try
        {
            var baseUrl = _config["Services:FeedbackServiceUrl"] ?? "http://localhost:8102";
            var apiKey = _config["InternalService:ApiKey"] ?? "lexor-internal-service-secret-key-2026";

            using var analyticsReq = new HttpRequestMessage(HttpMethod.Get, $"{baseUrl}/api/v1/admin/feedback/analytics");
            analyticsReq.Headers.Add("X-Internal-Service-Key", apiKey);
            var analyticsResp = await _httpClient.SendAsync(analyticsReq);
            if (analyticsResp.IsSuccessStatusCode)
            {
                var json = await analyticsResp.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(json);
                if (doc.RootElement.TryGetProperty("averageRating", out var ar) && ar.TryGetDecimal(out var avgVal))
                {
                    avg = avgVal;
                }
                if (doc.RootElement.TryGetProperty("ratingDistribution", out var rd) && rd.ValueKind == JsonValueKind.Object)
                {
                    foreach (var prop in rd.EnumerateObject())
                    {
                        if (prop.Value.TryGetInt64(out var cnt)) dist[prop.Name] = cnt;
                    }
                }
            }

            using var reportsReq = new HttpRequestMessage(HttpMethod.Get, $"{baseUrl}/api/v1/admin/feedback/reports?status=PENDING&size=1");
            reportsReq.Headers.Add("X-Internal-Service-Key", apiKey);
            var reportsResp = await _httpClient.SendAsync(reportsReq);
            if (reportsResp.IsSuccessStatusCode)
            {
                var json = await reportsResp.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(json);
                if (doc.RootElement.TryGetProperty("totalElements", out var te)) reports = te.GetInt64();
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning("Failed to query feedback metrics: {Message}", ex.Message);
        }

        return (avg, reports, dist);
    }
}
