using System.Security.Claims;
using AdminOperationsService.Clients;
using AdminOperationsService.Controllers;
using AdminOperationsService.Data;
using AdminOperationsService.DTOs;
using AdminOperationsService.Entities;
using AdminOperationsService.Services;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace AdminOperationsService.Tests;

public class AdminOperationsIntegrationTests
{
    private readonly ClaimsPrincipal _adminPrincipal;
    private readonly ClaimsPrincipal _riderPrincipal;
    private readonly ClaimsPrincipal _driverPrincipal;

    public AdminOperationsIntegrationTests()
    {
        _adminPrincipal = CreatePrincipal("admin-uuid-001", "ROLE_ADMIN");
        _riderPrincipal = CreatePrincipal("rider-uuid-001", "ROLE_RIDER");
        _driverPrincipal = CreatePrincipal("driver-uuid-001", "ROLE_DRIVER");
    }

    private static ClaimsPrincipal CreatePrincipal(string uuid, string role)
    {
        var claims = new List<Claim>
        {
            new(ClaimTypes.NameIdentifier, uuid),
            new("sub", uuid),
            new(ClaimTypes.Role, role),
            new("role", role)
        };
        var identity = new ClaimsIdentity(claims, "TestAuth");
        return new ClaimsPrincipal(identity);
    }

    private (
        AdminOperationsDbContext db,
        AdminOperationsController opsController,
        AdminAnalyticsController analyticsController,
        AdminHealthMonitorController healthController,
        AdminAuditLogController auditController
    ) CreateTestControllers()
    {
        var db = TestDbContextFactory.Create();

        // Seed initial setting
        db.AdminDashboardSettings.Add(new AdminDashboardSetting
        {
            SettingKey = "LIVE_OPS_POLL_INTERVAL_SECONDS",
            SettingValue = "10",
            Description = "Default polling interval"
        });
        db.SaveChanges();

        var config = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["Services:BookingServiceUrl"] = "http://localhost:8096",
                ["Services:PaymentServiceUrl"] = "http://localhost:8098",
                ["Services:DriverServiceUrl"] = "http://localhost:8086",
                ["Services:VehicleServiceUrl"] = "http://localhost:8088",
                ["Services:UserServiceUrl"] = "http://localhost:8084",
                ["Services:RideRequestServiceUrl"] = "http://localhost:8092",
                ["Services:FeedbackServiceUrl"] = "http://localhost:8102",
                ["InternalService:ApiKey"] = "lexor-internal-service-secret-key-2026"
            })
            .Build();

        var httpClient = new HttpClient { Timeout = TimeSpan.FromMilliseconds(50) };
        var bookingClient = new BookingAdminClient(httpClient, config, NullLogger<BookingAdminClient>.Instance);
        var paymentClient = new PaymentAdminClient(httpClient, config, NullLogger<PaymentAdminClient>.Instance);
        var driverClient = new DriverAdminClient(httpClient, config, NullLogger<DriverAdminClient>.Instance);
        var vehicleClient = new VehicleAdminClient(httpClient, config, NullLogger<VehicleAdminClient>.Instance);
        var userClient = new UserAdminClient(httpClient, config, NullLogger<UserAdminClient>.Instance);
        var rideRequestClient = new RideRequestAdminClient(httpClient, config, NullLogger<RideRequestAdminClient>.Instance);
        var feedbackClient = new FeedbackAdminClient(httpClient, config, NullLogger<FeedbackAdminClient>.Instance);
        var healthProbeClient = new ServiceHealthProbeClient(httpClient, db, config, NullLogger<ServiceHealthProbeClient>.Instance);

        var opsService = new OperationsDashboardService(
            bookingClient, paymentClient, driverClient, vehicleClient, userClient, rideRequestClient, feedbackClient,
            db, config, httpClient, NullLogger<OperationsDashboardService>.Instance);

        var analyticsService = new PlatformAnalyticsService(feedbackClient);
        var healthService = new ServiceHealthMonitorService(healthProbeClient);
        var auditService = new AdminAuditService(db);

        var opsController = new AdminOperationsController(opsService)
        {
            ControllerContext = new ControllerContext { HttpContext = new DefaultHttpContext { User = _adminPrincipal } }
        };
        var analyticsController = new AdminAnalyticsController(analyticsService)
        {
            ControllerContext = new ControllerContext { HttpContext = new DefaultHttpContext { User = _adminPrincipal } }
        };
        var healthController = new AdminHealthMonitorController(healthService)
        {
            ControllerContext = new ControllerContext { HttpContext = new DefaultHttpContext { User = _adminPrincipal } }
        };
        var auditController = new AdminAuditLogController(auditService)
        {
            ControllerContext = new ControllerContext { HttpContext = new DefaultHttpContext { User = _adminPrincipal } }
        };

        return (db, opsController, analyticsController, healthController, auditController);
    }

    [Fact]
    public async Task GetExecutiveOverview_Today_ReturnsSuccess()
    {
        var (_, opsController, _, _, _) = CreateTestControllers();

        var actionResult = await opsController.GetOverview("TODAY", null, null);
        var okResult = Assert.IsType<OkObjectResult>(actionResult.Result);
        var response = Assert.IsType<ApiResponse<ExecutiveOverviewResponse>>(okResult.Value);

        Assert.True(response.Success);
        Assert.NotNull(response.Data);
        Assert.Equal("TODAY", response.Data.TimeRange);
        Assert.NotEmpty(response.Data.MetricDefinitions);
    }

    [Fact]
    public async Task GetExecutiveOverview_TimeRanges_ReturnsSuccess()
    {
        var (_, opsController, _, _, _) = CreateTestControllers();

        var res7d = await opsController.GetOverview("7D", null, null);
        var ok7d = Assert.IsType<OkObjectResult>(res7d.Result);
        var body7d = Assert.IsType<ApiResponse<ExecutiveOverviewResponse>>(ok7d.Value);
        Assert.Equal("7D", body7d.Data!.TimeRange);

        var res30d = await opsController.GetOverview("30D", null, null);
        var ok30d = Assert.IsType<OkObjectResult>(res30d.Result);
        var body30d = Assert.IsType<ApiResponse<ExecutiveOverviewResponse>>(ok30d.Value);
        Assert.Equal("30D", body30d.Data!.TimeRange);
    }

    [Fact]
    public async Task GetExecutiveOverview_InvalidCustomRange_ThrowsArgumentException()
    {
        var (_, opsController, _, _, _) = CreateTestControllers();

        var start = DateTime.UtcNow.AddDays(5);
        var end = DateTime.UtcNow;

        await Assert.ThrowsAsync<ArgumentException>(() => opsController.GetOverview("CUSTOM", start, end));
    }

    [Fact]
    public async Task GetLiveOperationsSnapshot_ReturnsSnapshot()
    {
        var (_, opsController, _, _, _) = CreateTestControllers();

        var actionResult = await opsController.GetLiveSnapshot();
        var okResult = Assert.IsType<OkObjectResult>(actionResult.Result);
        var response = Assert.IsType<ApiResponse<LiveOperationsSnapshotResponse>>(okResult.Value);

        Assert.True(response.Success);
        Assert.NotNull(response.Data);
        Assert.Equal(10, response.Data.RefreshIntervalSeconds);
        Assert.NotNull(response.Data.DriverStates);
    }

    [Fact]
    public async Task EmergencyCancelRide_CreatesAuditLog()
    {
        var (db, opsController, _, _, _) = CreateTestControllers();

        var request = new EmergencyCancelRideRequest
        {
            BookingUuid = "b-test-cancel-uuid",
            Reason = "Admin test emergency ride cancel"
        };

        var actionResult = await opsController.EmergencyCancelRide(request);
        var okResult = Assert.IsType<OkObjectResult>(actionResult.Result);
        var response = Assert.IsType<ApiResponse<string>>(okResult.Value);

        Assert.True(response.Success);
        Assert.Contains("cancelled by administrator", response.Data);

        var log = db.AdminAuditLogs.FirstOrDefault(a => a.ResourceUuid == "b-test-cancel-uuid");
        Assert.NotNull(log);
        Assert.Equal("EMERGENCY_RIDE_CANCEL", log.Action);
    }

    [Fact]
    public async Task GetPlatformTrends_ReturnsTrends()
    {
        var (_, _, analyticsController, _, _) = CreateTestControllers();

        var actionResult = await analyticsController.GetTrends("7D");
        var okResult = Assert.IsType<OkObjectResult>(actionResult.Result);
        var response = Assert.IsType<ApiResponse<PlatformTrendsResponse>>(okResult.Value);

        Assert.True(response.Success);
        Assert.NotNull(response.Data);
        Assert.Equal("7D", response.Data.TimeRange);
        Assert.NotEmpty(response.Data.DailyRides);
        Assert.NotEmpty(response.Data.HourlyDemand);
        Assert.NotEmpty(response.Data.DailyFinancials);
    }

    [Fact]
    public async Task ExportCsv_Rides_ReturnsFileResult()
    {
        var (_, _, analyticsController, _, _) = CreateTestControllers();

        var actionResult = await analyticsController.ExportCsv("RIDES", "7D", null, null);
        var fileResult = Assert.IsType<FileStreamResult>(actionResult);

        Assert.Equal("text/csv", fileResult.ContentType);
        Assert.Equal("lexor-rides-export.csv", fileResult.FileDownloadName);
    }

    [Fact]
    public async Task ExportCsv_Payments_ReturnsFileResult()
    {
        var (_, _, analyticsController, _, _) = CreateTestControllers();

        var actionResult = await analyticsController.ExportCsv("PAYMENTS", "7D", null, null);
        var fileResult = Assert.IsType<FileStreamResult>(actionResult);

        Assert.Equal("text/csv", fileResult.ContentType);
        Assert.Equal("lexor-payments-export.csv", fileResult.FileDownloadName);
    }

    [Fact]
    public async Task ExportCsv_Drivers_ReturnsFileResult()
    {
        var (_, _, analyticsController, _, _) = CreateTestControllers();

        var actionResult = await analyticsController.ExportCsv("DRIVERS", "7D", null, null);
        var fileResult = Assert.IsType<FileStreamResult>(actionResult);

        Assert.Equal("text/csv", fileResult.ContentType);
        Assert.Equal("lexor-drivers-export.csv", fileResult.FileDownloadName);
    }

    [Fact]
    public async Task GetSystemHealth_ReturnsServices()
    {
        var (_, _, _, healthController, _) = CreateTestControllers();

        var actionResult = await healthController.GetSystemHealth();
        var okResult = Assert.IsType<OkObjectResult>(actionResult.Result);
        var response = Assert.IsType<ApiResponse<ServiceHealthStatusResponse>>(okResult.Value);

        Assert.True(response.Success);
        Assert.NotNull(response.Data);
        Assert.True(response.Data.TotalServices > 10);
        Assert.Contains(response.Data.Services, s => s.ServiceName.Contains("MySQL"));
    }

    [Fact]
    public async Task RecordAuditLog_Succeeds()
    {
        var (db, _, _, _, auditController) = CreateTestControllers();

        var request = new RecordAuditLogRequest
        {
            Action = "SUSPEND_DRIVER",
            ResourceType = "DRIVER",
            ResourceUuid = "d-test-uuid",
            Reason = "Investigation into road safety incident"
        };

        var actionResult = await auditController.RecordAudit(request);
        var okResult = Assert.IsType<OkObjectResult>(actionResult.Result);
        var response = Assert.IsType<ApiResponse<AdminAuditLogResponse>>(okResult.Value);

        Assert.True(response.Success);
        Assert.NotNull(response.Data);
        Assert.Equal("SUSPEND_DRIVER", response.Data.Action);

        var inDb = db.AdminAuditLogs.FirstOrDefault(a => a.ResourceUuid == "d-test-uuid");
        Assert.NotNull(inDb);
    }

    [Fact]
    public async Task ListAuditLogs_WithFilter_ReturnsPagedData()
    {
        var (_, _, _, _, auditController) = CreateTestControllers();

        await auditController.RecordAudit(new RecordAuditLogRequest
        {
            Action = "APPROVE_VEHICLE",
            ResourceType = "VEHICLE",
            ResourceUuid = "v-test-uuid",
            Reason = "Insurance documents inspected and verified"
        });

        var actionResult = await auditController.ListAuditLogs(action: "APPROVE_VEHICLE");
        var okResult = Assert.IsType<OkObjectResult>(actionResult.Result);
        var response = Assert.IsType<ApiResponse<PageResponse<AdminAuditLogResponse>>>(okResult.Value);

        Assert.True(response.Success);
        Assert.NotNull(response.Data);
        Assert.True(response.Data.TotalElements >= 1);
        Assert.Contains(response.Data.Content, a => a.Action == "APPROVE_VEHICLE");
    }

    [Fact]
    public async Task DashboardSettings_GetAndUpdate_Succeeds()
    {
        var (_, _, _, _, auditController) = CreateTestControllers();

        var listResult = await auditController.ListSettings();
        var okList = Assert.IsType<OkObjectResult>(listResult.Result);
        var listResponse = Assert.IsType<ApiResponse<List<AdminSettingResponse>>>(okList.Value);

        Assert.True(listResponse.Success);
        Assert.Contains(listResponse.Data!, s => s.SettingKey == "LIVE_OPS_POLL_INTERVAL_SECONDS");

        var updateReq = new UpdateSettingRequest
        {
            SettingKey = "LIVE_OPS_POLL_INTERVAL_SECONDS",
            SettingValue = "15",
            Description = "Updated live operations interval to 15s"
        };

        var updateResult = await auditController.UpdateSetting(updateReq);
        var okUpdate = Assert.IsType<OkObjectResult>(updateResult.Result);
        var updateResponse = Assert.IsType<ApiResponse<AdminSettingResponse>>(okUpdate.Value);

        Assert.True(updateResponse.Success);
        Assert.Equal("15", updateResponse.Data!.SettingValue);
    }
}
