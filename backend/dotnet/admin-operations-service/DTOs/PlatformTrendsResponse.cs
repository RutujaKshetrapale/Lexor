namespace AdminOperationsService.DTOs;

public class PlatformTrendsResponse
{
    public string TimeRange { get; set; } = "7D";
    public List<DailyMetricPoint> DailyRides { get; set; } = new();
    public List<HourlyDemandPoint> HourlyDemand { get; set; } = new();
    public decimal CompletionRatePercentage { get; set; }
    public decimal CancellationRatePercentage { get; set; }
    public List<DailyFinancialPoint> DailyFinancials { get; set; } = new();
    public Dictionary<string, long> StarDistribution { get; set; } = new();
}

public class DailyMetricPoint
{
    public string Date { get; set; } = string.Empty;
    public long TotalRides { get; set; }
    public long CompletedRides { get; set; }
    public long CancelledRides { get; set; }
}

public class HourlyDemandPoint
{
    public int Hour { get; set; }
    public long RideRequestsCount { get; set; }
}

public class DailyFinancialPoint
{
    public string Date { get; set; } = string.Empty;
    public decimal GrossBookingValue { get; set; }
    public decimal Collections { get; set; }
    public decimal Refunds { get; set; }
}

public class ServiceHealthStatusResponse
{
    public DateTime CheckedAt { get; set; } = DateTime.UtcNow;
    public int TotalServices { get; set; }
    public int HealthyServices { get; set; }
    public int DegradedServices { get; set; }
    public int DownServices { get; set; }
    public string OverallSystemStatus { get; set; } = "UP";
    public List<ServiceHealthItem> Services { get; set; } = new();
}

public class ServiceHealthItem
{
    public string ServiceName { get; set; } = string.Empty;
    public int Port { get; set; }
    public string EndpointUrl { get; set; } = string.Empty;
    public string Status { get; set; } = "UP";
    public long LatencyMs { get; set; }
    public string Readiness { get; set; } = "READY";
    public string? ErrorDetails { get; set; }
    public DateTime LastChecked { get; set; } = DateTime.UtcNow;
}

public class AdminAuditLogResponse
{
    public string AuditUuid { get; set; } = string.Empty;
    public string AdminUserUuid { get; set; } = string.Empty;
    public string Action { get; set; } = string.Empty;
    public string ResourceType { get; set; } = string.Empty;
    public string? ResourceUuid { get; set; }
    public string? Reason { get; set; }
    public string? Details { get; set; }
    public string? IpAddress { get; set; }
    public string Status { get; set; } = "SUCCESS";
    public DateTime CreatedAt { get; set; }
}

public class AdminSettingResponse
{
    public string SettingKey { get; set; } = string.Empty;
    public string SettingValue { get; set; } = string.Empty;
    public string? Description { get; set; }
    public string? UpdatedByUuid { get; set; }
    public DateTime UpdatedAt { get; set; }
}
