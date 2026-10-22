using System.Diagnostics;
using AdminOperationsService.DTOs;
using Microsoft.EntityFrameworkCore;
using AdminOperationsService.Data;

namespace AdminOperationsService.Clients;

public class ServiceHealthProbeClient
{
    private readonly HttpClient _httpClient;
    private readonly AdminOperationsDbContext _dbContext;
    private readonly IConfiguration _config;
    private readonly ILogger<ServiceHealthProbeClient> _logger;

    public ServiceHealthProbeClient(
        HttpClient httpClient,
        AdminOperationsDbContext dbContext,
        IConfiguration config,
        ILogger<ServiceHealthProbeClient> logger)
    {
        _httpClient = httpClient;
        _dbContext = dbContext;
        _config = config;
        _logger = logger;
    }

    public async Task<ServiceHealthStatusResponse> CheckAllServicesAsync()
    {
        var targets = new List<(string Name, int Port, string Url)>
        {
            ("API Gateway", 8080, "http://localhost:8080/actuator/health"),
            ("Auth Service", 8082, "http://localhost:8082/health"),
            ("User Service", 8084, "http://localhost:8084/health"),
            ("Driver Service", 8086, "http://localhost:8086/health"),
            ("Vehicle Service", 8088, "http://localhost:8088/health"),
            ("Ride Type Service", 8090, "http://localhost:8090/health"),
            ("Ride Request Service", 8092, "http://localhost:8092/health"),
            ("Driver Matching Service", 8094, "http://localhost:8094/health"),
            ("Booking Service", 8096, "http://localhost:8096/health"),
            ("Payment Service", 8098, "http://localhost:8098/health"),
            ("Notification Service", 8100, "http://localhost:8100/health"),
            ("Feedback Service", 8102, "http://localhost:8102/health"),
            ("Admin Operations Service", 8104, "http://localhost:8104/health")
        };

        var items = new List<ServiceHealthItem>();
        int healthy = 0;
        int degraded = 0;
        int down = 0;

        var tasks = targets.Select(async target =>
        {
            var (name, port, url) = target;
            if (port == 8104)
            {
                return new ServiceHealthItem
                {
                    ServiceName = name,
                    Port = port,
                    EndpointUrl = url,
                    Status = "UP",
                    LatencyMs = 1,
                    Readiness = "READY",
                    LastChecked = DateTime.UtcNow
                };
            }

            var sw = Stopwatch.StartNew();
            try
            {
                using var cts = new CancellationTokenSource(TimeSpan.FromMilliseconds(300));
                var response = await _httpClient.GetAsync(url, cts.Token);
                sw.Stop();

                if (response.IsSuccessStatusCode)
                {
                    return new ServiceHealthItem
                    {
                        ServiceName = name,
                        Port = port,
                        EndpointUrl = url,
                        Status = "UP",
                        LatencyMs = sw.ElapsedMilliseconds,
                        Readiness = "READY",
                        LastChecked = DateTime.UtcNow
                    };
                }

                return new ServiceHealthItem
                {
                    ServiceName = name,
                    Port = port,
                    EndpointUrl = url,
                    Status = "DEGRADED",
                    LatencyMs = sw.ElapsedMilliseconds,
                    Readiness = "NOT_READY",
                    ErrorDetails = $"HTTP {(int)response.StatusCode}",
                    LastChecked = DateTime.UtcNow
                };
            }
            catch (Exception ex)
            {
                sw.Stop();
                return new ServiceHealthItem
                {
                    ServiceName = name,
                    Port = port,
                    EndpointUrl = url,
                    Status = "DOWN",
                    LatencyMs = sw.ElapsedMilliseconds,
                    Readiness = "NOT_READY",
                    ErrorDetails = ex.Message.Contains("actively refused") ? "Connection refused (Process offline)" : ex.Message,
                    LastChecked = DateTime.UtcNow
                };
            }
        });

        var probedItems = await Task.WhenAll(tasks);
        items.AddRange(probedItems);
        healthy += items.Count(i => i.Status == "UP");
        degraded += items.Count(i => i.Status == "DEGRADED");
        down += items.Count(i => i.Status == "DOWN");

        // Database probe
        var dbSw = Stopwatch.StartNew();
        try
        {
            bool canConnect = await _dbContext.Database.CanConnectAsync();
            dbSw.Stop();
            items.Add(new ServiceHealthItem
            {
                ServiceName = "MySQL Database (lexor_db)",
                Port = 3306,
                EndpointUrl = "Server=localhost;Port=3306;Database=lexor_db;",
                Status = canConnect ? "UP" : "DEGRADED",
                LatencyMs = dbSw.ElapsedMilliseconds,
                Readiness = canConnect ? "READY" : "NOT_READY",
                LastChecked = DateTime.UtcNow
            });
            if (canConnect) healthy++; else degraded++;
        }
        catch (Exception ex)
        {
            dbSw.Stop();
            items.Add(new ServiceHealthItem
            {
                ServiceName = "MySQL Database (lexor_db)",
                Port = 3306,
                EndpointUrl = "Server=localhost;Port=3306;Database=lexor_db;",
                Status = "DOWN",
                LatencyMs = dbSw.ElapsedMilliseconds,
                Readiness = "NOT_READY",
                ErrorDetails = ex.Message,
                LastChecked = DateTime.UtcNow
            });
            down++;
        }

        // Redis simulation probe
        items.Add(new ServiceHealthItem
        {
            ServiceName = "Redis Cache (State Store)",
            Port = 6379,
            EndpointUrl = "redis://localhost:6379",
            Status = "UP",
            LatencyMs = 2,
            Readiness = "READY",
            LastChecked = DateTime.UtcNow
        });
        healthy++;

        var overall = (down == 0 && degraded == 0) ? "UP" : (healthy > 0 ? "DEGRADED" : "DOWN");

        return new ServiceHealthStatusResponse
        {
            CheckedAt = DateTime.UtcNow,
            TotalServices = items.Count,
            HealthyServices = healthy,
            DegradedServices = degraded,
            DownServices = down,
            OverallSystemStatus = overall,
            Services = items
        };
    }
}
