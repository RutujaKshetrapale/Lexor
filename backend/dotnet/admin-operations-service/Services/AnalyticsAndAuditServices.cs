using System.Security.Claims;
using AdminOperationsService.Clients;
using AdminOperationsService.Data;
using AdminOperationsService.DTOs;
using AdminOperationsService.Entities;
using Microsoft.EntityFrameworkCore;

namespace AdminOperationsService.Services;

public interface IPlatformAnalyticsService
{
    Task<PlatformTrendsResponse> GetPlatformTrendsAsync(string? timeRange);
    Task ExportCsvAsync(string entityType, string? timeRange, DateTime? startDate, DateTime? endDate, TextWriter writer);
}

public class PlatformAnalyticsService : IPlatformAnalyticsService
{
    private readonly FeedbackAdminClient _feedbackClient;

    public PlatformAnalyticsService(FeedbackAdminClient feedbackClient)
    {
        _feedbackClient = feedbackClient;
    }

    public async Task<PlatformTrendsResponse> GetPlatformTrendsAsync(string? timeRange)
    {
        int days = 7;
        if (string.Equals(timeRange, "30D", StringComparison.OrdinalIgnoreCase)) days = 30;
        if (string.Equals(timeRange, "90D", StringComparison.OrdinalIgnoreCase)) days = 90;

        var dailyRides = new List<DailyMetricPoint>();
        var dailyFinancials = new List<DailyFinancialPoint>();

        var today = DateTime.UtcNow.Date;
        long totalSum = 0;
        long completedSum = 0;
        long cancelledSum = 0;

        for (int i = days - 1; i >= 0; i--)
        {
            var d = today.AddDays(-i);
            var dateStr = d.ToString("yyyy-MM-dd");

            long dayTotal = 15 + (i * 3L % 17);
            long dayCompleted = (long)(dayTotal * 0.88);
            long dayCancelled = dayTotal - dayCompleted;

            totalSum += dayTotal;
            completedSum += dayCompleted;
            cancelledSum += dayCancelled;

            dailyRides.Add(new DailyMetricPoint
            {
                Date = dateStr,
                TotalRides = dayTotal,
                CompletedRides = dayCompleted,
                CancelledRides = dayCancelled
            });

            decimal gross = Math.Round(dayCompleted * 240.0m, 2);
            decimal collections = Math.Round(dayCompleted * 235.0m, 2);
            decimal refunds = Math.Round(dayCancelled * 15.0m, 2);

            dailyFinancials.Add(new DailyFinancialPoint
            {
                Date = dateStr,
                GrossBookingValue = gross,
                Collections = collections,
                Refunds = refunds
            });
        }

        var hourlyDemand = new List<HourlyDemandPoint>();
        for (int h = 0; h < 24; h++)
        {
            long demand = (h >= 8 && h <= 10) ? 45 + (h * 4) :
                          (h >= 17 && h <= 20) ? 55 + (h * 3) :
                          (h >= 0 && h <= 5) ? 5 + h : 22 + (h * 2);

            hourlyDemand.Add(new HourlyDemandPoint
            {
                Hour = h,
                RideRequestsCount = demand
            });
        }

        decimal completionRate = totalSum > 0 ? Math.Round((decimal)completedSum / totalSum * 100.0m, 2) : 100.00m;
        decimal cancellationRate = totalSum > 0 ? Math.Round((decimal)cancelledSum / totalSum * 100.0m, 2) : 0.00m;

        var (_, _, starDist) = await _feedbackClient.GetFeedbackMetricsAsync();

        return new PlatformTrendsResponse
        {
            TimeRange = !string.IsNullOrWhiteSpace(timeRange) ? timeRange.ToUpper() : "7D",
            DailyRides = dailyRides,
            HourlyDemand = hourlyDemand,
            CompletionRatePercentage = completionRate,
            CancellationRatePercentage = cancellationRate,
            DailyFinancials = dailyFinancials,
            StarDistribution = starDist
        };
    }

    public async Task ExportCsvAsync(string entityType, string? timeRange, DateTime? startDate, DateTime? endDate, TextWriter writer)
    {
        if (string.IsNullOrWhiteSpace(entityType))
        {
            throw new ArgumentException("Entity type is required for CSV export (e.g., RIDES, PAYMENTS, DRIVERS)");
        }

        switch (entityType.ToUpper())
        {
            case "RIDES":
                await writer.WriteLineAsync("Booking_UUID,Rider_UUID,Driver_UUID,Pickup_Address,Dropoff_Address,Status,Fare_Amount,Created_At");
                await writer.WriteLineAsync($"b001-mock,r001-rider,d001-driver,\"Central Station\",\"Airport Terminal 2\",COMPLETED,450.00,{DateTime.UtcNow.AddHours(-2):o}");
                await writer.WriteLineAsync($"b002-mock,r002-rider,d002-driver,\"Silicon Hub\",\"Tech Park Gate 3\",COMPLETED,280.00,{DateTime.UtcNow.AddHours(-1):o}");
                break;
            case "PAYMENTS":
                await writer.WriteLineAsync("Payment_UUID,Booking_UUID,Amount,Method,Status,Commission,Driver_Earnings,Created_At");
                await writer.WriteLineAsync($"p001-mock,b001-mock,450.00,UPI,COMPLETED,90.00,360.00,{DateTime.UtcNow.AddHours(-2):o}");
                await writer.WriteLineAsync($"p002-mock,b002-mock,280.00,CARD,COMPLETED,56.00,224.00,{DateTime.UtcNow.AddHours(-1):o}");
                break;
            case "DRIVERS":
                await writer.WriteLineAsync("Driver_UUID,License_Number,Status,Onboarding_Status,Rating,Total_Trips,Created_At");
                await writer.WriteLineAsync($"d001-driver,DL-99201-MH,ONLINE,APPROVED,4.92,142,{DateTime.UtcNow.AddDays(-30):o}");
                await writer.WriteLineAsync($"d002-driver,DL-44812-KA,OFFLINE,APPROVED,4.85,98,{DateTime.UtcNow.AddDays(-20):o}");
                break;
            default:
                throw new ArgumentException($"Unsupported CSV export entity type: {entityType}");
        }
        await writer.FlushAsync();
    }
}

public interface IServiceHealthMonitorService
{
    Task<ServiceHealthStatusResponse> GetSystemHealthAsync();
}

public class ServiceHealthMonitorService : IServiceHealthMonitorService
{
    private readonly ServiceHealthProbeClient _healthProbeClient;

    public ServiceHealthMonitorService(ServiceHealthProbeClient healthProbeClient)
    {
        _healthProbeClient = healthProbeClient;
    }

    public Task<ServiceHealthStatusResponse> GetSystemHealthAsync()
    {
        return _healthProbeClient.CheckAllServicesAsync();
    }
}

public interface IAdminAuditService
{
    Task<AdminAuditLogResponse> RecordAuditLogAsync(ClaimsPrincipal admin, RecordAuditLogRequest request, string? ipAddress);
    Task<PageResponse<AdminAuditLogResponse>> GetAuditLogsAsync(string? adminUserUuid, string? action, string? resourceType, DateTime? startDate, DateTime? endDate, int page, int size);
    Task<List<AdminSettingResponse>> GetAllSettingsAsync();
    Task<AdminSettingResponse> UpdateSettingAsync(ClaimsPrincipal admin, UpdateSettingRequest request);
}

public class AdminAuditService : IAdminAuditService
{
    private readonly AdminOperationsDbContext _dbContext;

    public AdminAuditService(AdminOperationsDbContext dbContext)
    {
        _dbContext = dbContext;
    }

    public async Task<AdminAuditLogResponse> RecordAuditLogAsync(ClaimsPrincipal admin, RecordAuditLogRequest request, string? ipAddress)
    {
        var adminUuid = admin.FindFirst(ClaimTypes.NameIdentifier)?.Value ?? "admin-system";

        var log = new AdminAuditLog
        {
            AuditUuid = Guid.NewGuid().ToString(),
            AdminUserUuid = adminUuid,
            Action = request.Action,
            ResourceType = request.ResourceType,
            ResourceUuid = request.ResourceUuid,
            Reason = request.Reason,
            Details = request.Details,
            IpAddress = ipAddress,
            Status = "SUCCESS",
            CreatedAt = DateTime.UtcNow
        };

        _dbContext.AdminAuditLogs.Add(log);
        await _dbContext.SaveChangesAsync();

        return MapToDto(log);
    }

    public async Task<PageResponse<AdminAuditLogResponse>> GetAuditLogsAsync(
        string? adminUserUuid, string? action, string? resourceType, DateTime? startDate, DateTime? endDate, int page, int size)
    {
        var query = _dbContext.AdminAuditLogs.AsQueryable();

        if (!string.IsNullOrWhiteSpace(adminUserUuid)) query = query.Where(a => a.AdminUserUuid == adminUserUuid);
        if (!string.IsNullOrWhiteSpace(action)) query = query.Where(a => a.Action == action);
        if (!string.IsNullOrWhiteSpace(resourceType)) query = query.Where(a => a.ResourceType == resourceType);
        if (startDate.HasValue) query = query.Where(a => a.CreatedAt >= startDate.Value);
        if (endDate.HasValue) query = query.Where(a => a.CreatedAt <= endDate.Value);

        long totalElements = await query.CountAsync();
        int totalPages = (int)Math.Ceiling((double)totalElements / Math.Max(1, size));

        var items = await query.OrderByDescending(a => a.CreatedAt)
            .Skip(page * size)
            .Take(size)
            .ToListAsync();

        return new PageResponse<AdminAuditLogResponse>
        {
            Content = items.Select(MapToDto).ToList(),
            Page = page,
            Size = size,
            TotalElements = totalElements,
            TotalPages = totalPages,
            First = page == 0,
            Last = page >= totalPages - 1
        };
    }

    public async Task<List<AdminSettingResponse>> GetAllSettingsAsync()
    {
        var items = await _dbContext.AdminDashboardSettings.ToListAsync();
        return items.Select(s => new AdminSettingResponse
        {
            SettingKey = s.SettingKey,
            SettingValue = s.SettingValue,
            Description = s.Description,
            UpdatedByUuid = s.UpdatedByUuid,
            UpdatedAt = s.UpdatedAt
        }).ToList();
    }

    public async Task<AdminSettingResponse> UpdateSettingAsync(ClaimsPrincipal admin, UpdateSettingRequest request)
    {
        var adminUuid = admin.FindFirst(ClaimTypes.NameIdentifier)?.Value ?? "admin-system";

        var setting = await _dbContext.AdminDashboardSettings
            .FirstOrDefaultAsync(s => s.SettingKey == request.SettingKey);

        if (setting == null)
        {
            setting = new AdminDashboardSetting
            {
                SettingKey = request.SettingKey,
                Description = request.Description
            };
            _dbContext.AdminDashboardSettings.Add(setting);
        }

        setting.SettingValue = request.SettingValue;
        if (!string.IsNullOrWhiteSpace(request.Description)) setting.Description = request.Description;
        setting.UpdatedByUuid = adminUuid;
        setting.UpdatedAt = DateTime.UtcNow;

        await _dbContext.SaveChangesAsync();

        await RecordAuditLogAsync(admin, new RecordAuditLogRequest
        {
            Action = "UPDATE_SETTING",
            ResourceType = "DASHBOARD_SETTING",
            ResourceUuid = setting.SettingKey,
            Reason = $"Administrator updated parameter: {request.SettingKey} = {request.SettingValue}"
        }, null);

        return new AdminSettingResponse
        {
            SettingKey = setting.SettingKey,
            SettingValue = setting.SettingValue,
            Description = setting.Description,
            UpdatedByUuid = setting.UpdatedByUuid,
            UpdatedAt = setting.UpdatedAt
        };
    }

    private static AdminAuditLogResponse MapToDto(AdminAuditLog entity)
    {
        return new AdminAuditLogResponse
        {
            AuditUuid = entity.AuditUuid,
            AdminUserUuid = entity.AdminUserUuid,
            Action = entity.Action,
            ResourceType = entity.ResourceType,
            ResourceUuid = entity.ResourceUuid,
            Reason = entity.Reason,
            Details = entity.Details,
            IpAddress = entity.IpAddress,
            Status = entity.Status,
            CreatedAt = entity.CreatedAt
        };
    }
}
