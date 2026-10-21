using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using PaymentService.Data;
using PaymentService.Entities;

namespace PaymentService.Services;

public interface IIdempotencyService
{
    Task<T?> FindCachedResponseAsync<T>(string callerUuid, string idempotencyKey);
    Task StoreResponseAsync(string callerUuid, string idempotencyKey, string resourceType, int statusCode, object payload);
}

public class IdempotencyService : IIdempotencyService
{
    private readonly PaymentDbContext _dbContext;
    private readonly ILogger<IdempotencyService> _logger;

    public IdempotencyService(PaymentDbContext dbContext, ILogger<IdempotencyService> logger)
    {
        _dbContext = dbContext;
        _logger = logger;
    }

    public async Task<T?> FindCachedResponseAsync<T>(string callerUuid, string idempotencyKey)
    {
        if (string.IsNullOrWhiteSpace(callerUuid) || string.IsNullOrWhiteSpace(idempotencyKey))
        {
            return default;
        }

        var record = await _dbContext.PaymentIdempotencyRecords
            .AsNoTracking()
            .FirstOrDefaultAsync(r => r.CallerUuid == callerUuid && r.IdempotencyKey == idempotencyKey);

        if (record != null && record.ExpiresAt > DateTime.UtcNow)
        {
            try
            {
                var cached = JsonSerializer.Deserialize<T>(record.ResponsePayload, new JsonSerializerOptions
                {
                    PropertyNameCaseInsensitive = true
                });
                _logger.LogInformation("Found cached idempotent response for caller {CallerUuid} and key {Key}", callerUuid, idempotencyKey);
                return cached;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to deserialize cached idempotent response");
            }
        }
        return default;
    }

    public async Task StoreResponseAsync(string callerUuid, string idempotencyKey, string resourceType, int statusCode, object payload)
    {
        if (string.IsNullOrWhiteSpace(callerUuid) || string.IsNullOrWhiteSpace(idempotencyKey) || payload == null)
        {
            return;
        }

        try
        {
            var json = JsonSerializer.Serialize(payload, new JsonSerializerOptions
            {
                PropertyNamingPolicy = JsonNamingPolicy.CamelCase
            });

            var record = new PaymentIdempotencyRecord
            {
                CallerUuid = callerUuid,
                IdempotencyKey = idempotencyKey,
                ResourceType = resourceType,
                ResponseStatusCode = statusCode,
                ResponsePayload = json,
                ExpiresAt = DateTime.UtcNow.AddHours(24)
            };

            _dbContext.PaymentIdempotencyRecords.Add(record);
            await _dbContext.SaveChangesAsync();
            _logger.LogInformation("Stored idempotent response for caller {CallerUuid} and key {Key}", callerUuid, idempotencyKey);
        }
        catch (Exception ex)
        {
            _logger.LogWarning("Could not store idempotency record: {Message}", ex.Message);
        }
    }
}
