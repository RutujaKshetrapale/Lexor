using System.Security.Claims;
using AdminOperationsService.Clients;
using AdminOperationsService.Data;
using AdminOperationsService.DTOs;
using AdminOperationsService.Entities;

namespace AdminOperationsService.Services;

public interface IOperationsDashboardService
{
    Task<ExecutiveOverviewResponse> GetExecutiveOverviewAsync(string? timeRange, DateTime? customStart, DateTime? customEnd);
    Task<LiveOperationsSnapshotResponse> GetLiveOperationsSnapshotAsync();
    Task EmergencyCancelRideAsync(ClaimsPrincipal admin, EmergencyCancelRideRequest request);
}

public class OperationsDashboardService : IOperationsDashboardService
{
    private readonly BookingAdminClient _bookingClient;
    private readonly PaymentAdminClient _paymentClient;
    private readonly DriverAdminClient _driverClient;
    private readonly VehicleAdminClient _vehicleClient;
    private readonly UserAdminClient _userClient;
    private readonly RideRequestAdminClient _rideRequestClient;
    private readonly FeedbackAdminClient _feedbackClient;
    private readonly AdminOperationsDbContext _dbContext;
    private readonly IConfiguration _config;
    private readonly HttpClient _httpClient;
    private readonly ILogger<OperationsDashboardService> _logger;

    public OperationsDashboardService(
        BookingAdminClient bookingClient,
        PaymentAdminClient paymentClient,
        DriverAdminClient driverClient,
        VehicleAdminClient vehicleClient,
        UserAdminClient userClient,
        RideRequestAdminClient rideRequestClient,
        FeedbackAdminClient feedbackClient,
        AdminOperationsDbContext dbContext,
        IConfiguration config,
        HttpClient httpClient,
        ILogger<OperationsDashboardService> logger)
    {
        _bookingClient = bookingClient;
        _paymentClient = paymentClient;
        _driverClient = driverClient;
        _vehicleClient = vehicleClient;
        _userClient = userClient;
        _rideRequestClient = rideRequestClient;
        _feedbackClient = feedbackClient;
        _dbContext = dbContext;
        _config = config;
        _httpClient = httpClient;
        _logger = logger;
    }

    public async Task<ExecutiveOverviewResponse> GetExecutiveOverviewAsync(string? timeRange, DateTime? customStart, DateTime? customEnd)
    {
        var rangeKey = !string.IsNullOrWhiteSpace(timeRange) ? timeRange.ToUpper() : "TODAY";
        DateTime start;
        DateTime end = DateTime.UtcNow;

        switch (rangeKey)
        {
            case "7D":
                start = end.AddDays(-7);
                break;
            case "30D":
                start = end.AddDays(-30);
                break;
            case "90D":
                start = end.AddDays(-90);
                break;
            case "CUSTOM":
                if (!customStart.HasValue || !customEnd.HasValue || customStart.Value > customEnd.Value)
                {
                    throw new ArgumentException("Invalid custom date range specified: startDate must be before endDate");
                }
                start = customStart.Value;
                end = customEnd.Value;
                break;
            default:
                rangeKey = "TODAY";
                start = DateTime.UtcNow.Date;
                break;
        }

        var (totalRides, completedRides, cancelledRides, activeRides, gross) = await _bookingClient.GetBookingMetricsAsync(start, end);
        var (collections, refunds, commission, _) = await _paymentClient.GetFinancialReconciliationAsync();
        var (totalDrivers, onlineDrivers, inTripDrivers, pendingVerif) = await _driverClient.GetDriverMetricsAsync();
        var vehiclesCount = await _vehicleClient.GetRegisteredVehiclesCountAsync();
        var (ridersCount, userDriversCount) = await _userClient.GetUserCountsAsync();
        var pendingRequests = await _rideRequestClient.GetPendingRequestsCountAsync();
        var (avgRating, pendingReports, _) = await _feedbackClient.GetFeedbackMetricsAsync();

        var definitions = new Dictionary<string, string>
        {
            ["totalRides"] = "Authoritative bookings recorded in booking-service across all non-purged statuses.",
            ["completedRides"] = "Bookings reaching terminal COMPLETED status.",
            ["cancelledRides"] = "Bookings terminated with CANCELLED_BY_RIDER, CANCELLED_BY_DRIVER, CANCELLED_BY_ADMIN, or EXPIRED.",
            ["activeRides"] = "Bookings in CONFIRMED, ACCEPTED, ARRIVED, or IN_PROGRESS status.",
            ["grossBookingValue"] = "Total calculated fare amount of all confirmed platform rides before deductions.",
            ["successfulPaymentTotals"] = "Total transaction volume collected by payment-service in COMPLETED state.",
            ["refundTotals"] = "Total monetary sum disbursed to riders via payment refund workflows.",
            ["platformCommissionEarned"] = "Platform cut retained by LEXOR according to platform commission rates.",
            ["averageDriverRating"] = "Decimal-safe average (HALF_UP) of all approved visible ratings from feedback-service."
        };

        return new ExecutiveOverviewResponse
        {
            TotalRides = totalRides,
            CompletedRides = completedRides,
            CancelledRides = cancelledRides,
            ActiveRides = activeRides,
            PendingRideRequests = pendingRequests,
            OnlineDrivers = onlineDrivers,
            InTripDrivers = inTripDrivers,
            PendingDriverVerifications = pendingVerif,
            RegisteredRiders = ridersCount,
            RegisteredDrivers = totalDrivers > 0 ? totalDrivers : userDriversCount,
            RegisteredVehicles = vehiclesCount,
            GrossBookingValue = gross,
            SuccessfulPaymentTotals = collections,
            RefundTotals = refunds,
            PlatformCommissionEarned = commission,
            AverageDriverRating = avgRating,
            FeedbackReportsAwaitingModeration = pendingReports,
            TimeRange = rangeKey,
            RangeStartDate = start,
            RangeEndDate = end,
            TimeZone = TimeZoneInfo.Utc.Id,
            CalculatedAt = DateTime.UtcNow,
            MetricDefinitions = definitions
        };
    }

    public async Task<LiveOperationsSnapshotResponse> GetLiveOperationsSnapshotAsync()
    {
        var (totalDrivers, online, inTrip, _) = await _driverClient.GetDriverMetricsAsync();
        long offline = Math.Max(0, totalDrivers - (online + inTrip));

        var activeRides = await _bookingClient.GetActiveRidesAsync();
        var pendingRequests = await _rideRequestClient.GetPendingRequestsAsync();
        var cancellations = await _bookingClient.GetRecentCancellationsAsync();

        var alerts = new List<OperationalAlert>();
        if (pendingRequests.Count > 10)
        {
            alerts.Add(new OperationalAlert
            {
                AlertType = "MATCHING_SURGE",
                Severity = "WARNING",
                Message = $"High volume of pending ride requests ({pendingRequests.Count}) awaiting driver assignment",
                DetectedAt = DateTime.UtcNow
            });
        }
        if (online == 0 && activeRides.Count > 0)
        {
            alerts.Add(new OperationalAlert
            {
                AlertType = "LOW_DRIVER_AVAILABILITY",
                Severity = "CRITICAL",
                Message = "Zero online available drivers with active rides currently in progress",
                DetectedAt = DateTime.UtcNow
            });
        }

        return new LiveOperationsSnapshotResponse
        {
            SnapshotTimestamp = DateTime.UtcNow,
            RefreshIntervalSeconds = 10,
            DriverStates = new DriverStateBreakdown
            {
                OnlineAvailable = online,
                BusyInTrip = inTrip,
                Offline = offline,
                TotalDrivers = totalDrivers
            },
            ActiveRides = activeRides,
            PendingRequests = pendingRequests,
            RecentCancellations = cancellations,
            ActiveAlerts = alerts
        };
    }

    public async Task EmergencyCancelRideAsync(ClaimsPrincipal admin, EmergencyCancelRideRequest request)
    {
        var adminUuid = admin.FindFirst(ClaimTypes.NameIdentifier)?.Value ?? "admin-system";
        _logger.LogInformation("Administrator {AdminUuid} initiated emergency cancellation for booking {BookingUuid}", adminUuid, request.BookingUuid);

        try
        {
            var baseUrl = _config["Services:BookingServiceUrl"] ?? "http://localhost:8096";
            var apiKey = _config["InternalService:ApiKey"] ?? "lexor-internal-service-secret-key-2026";

            using var httpRequest = new HttpRequestMessage(HttpMethod.Post, $"{baseUrl}/api/v1/bookings/{request.BookingUuid}/cancel");
            httpRequest.Headers.Add("X-Internal-Service-Key", apiKey);
            httpRequest.Content = JsonContent.Create(new { cancellationReason = "ADMIN_OVERRIDE: " + request.Reason });

            await _httpClient.SendAsync(httpRequest);
        }
        catch (Exception ex)
        {
            _logger.LogWarning("Emergency booking cancellation call failed: {Message}", ex.Message);
        }

        var logEntry = new AdminAuditLog
        {
            AuditUuid = Guid.NewGuid().ToString(),
            AdminUserUuid = adminUuid,
            Action = "EMERGENCY_RIDE_CANCEL",
            ResourceType = "BOOKING",
            ResourceUuid = request.BookingUuid,
            Reason = request.Reason,
            Status = "SUCCESS",
            CreatedAt = DateTime.UtcNow
        };

        _dbContext.AdminAuditLogs.Add(logEntry);
        await _dbContext.SaveChangesAsync();
    }
}
