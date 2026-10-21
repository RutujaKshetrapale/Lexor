using Microsoft.EntityFrameworkCore;
using NotificationService.Data;
using NotificationService.Domain.Entities;
using NotificationService.DTOs;
using NotificationService.Exceptions;

namespace NotificationService.Services;

public interface IPreferenceService
{
    Task<NotificationPreferenceDto> GetPreferencesAsync(string userUuid);
    Task<NotificationPreference> GetOrCreateEntityAsync(string userUuid);
    Task<NotificationPreferenceDto> UpdatePreferencesAsync(string userUuid, UpdatePreferencesRequest request);
}

public class PreferenceService : IPreferenceService
{
    private readonly NotificationDbContext _dbContext;
    private readonly ILogger<PreferenceService> _logger;

    public PreferenceService(NotificationDbContext dbContext, ILogger<PreferenceService> logger)
    {
        _dbContext = dbContext;
        _logger = logger;
    }

    public async Task<NotificationPreferenceDto> GetPreferencesAsync(string userUuid)
    {
        var entity = await GetOrCreateEntityAsync(userUuid);
        return MapToDto(entity);
    }

    public async Task<NotificationPreference> GetOrCreateEntityAsync(string userUuid)
    {
        var existing = await _dbContext.NotificationPreferences.FirstOrDefaultAsync(p => p.UserUuid == userUuid);
        if (existing != null) return existing;

        var newPref = new NotificationPreference
        {
            PreferenceUuid = Guid.NewGuid().ToString(),
            UserUuid = userUuid,
            PushEnabled = true,
            EmailEnabled = true,
            SmsEnabled = true,
            RideUpdatesEnabled = true,
            PaymentUpdatesEnabled = true,
            PromotionalEnabled = false,
            SecurityAlertsEnabled = true,
            QuietHoursEnabled = false,
            PreferredLocale = "en"
        };

        _dbContext.NotificationPreferences.Add(newPref);
        await _dbContext.SaveChangesAsync();
        return newPref;
    }

    public async Task<NotificationPreferenceDto> UpdatePreferencesAsync(string userUuid, UpdatePreferencesRequest request)
    {
        var entity = await GetOrCreateEntityAsync(userUuid);

        if (request.PushEnabled.HasValue) entity.PushEnabled = request.PushEnabled.Value;
        if (request.EmailEnabled.HasValue) entity.EmailEnabled = request.EmailEnabled.Value;
        if (request.SmsEnabled.HasValue) entity.SmsEnabled = request.SmsEnabled.Value;
        if (request.RideUpdatesEnabled.HasValue) entity.RideUpdatesEnabled = request.RideUpdatesEnabled.Value;
        if (request.PaymentUpdatesEnabled.HasValue) entity.PaymentUpdatesEnabled = request.PaymentUpdatesEnabled.Value;
        if (request.PromotionalEnabled.HasValue) entity.PromotionalEnabled = request.PromotionalEnabled.Value;

        // Security alerts can never be disabled
        entity.SecurityAlertsEnabled = true;

        if (request.QuietHoursEnabled.HasValue) entity.QuietHoursEnabled = request.QuietHoursEnabled.Value;
        if (request.QuietHoursStart.HasValue) entity.QuietHoursStart = request.QuietHoursStart.Value;
        if (request.QuietHoursEnd.HasValue) entity.QuietHoursEnd = request.QuietHoursEnd.Value;

        if (!string.IsNullOrWhiteSpace(request.PreferredLocale))
        {
            var loc = request.PreferredLocale.ToLower().Trim();
            if (loc is not ("en" or "hi" or "mr"))
            {
                throw new BadRequestException("Invalid locale format. Supported formats: en, hi, mr");
            }
            entity.PreferredLocale = loc;
        }

        entity.UpdatedAt = DateTime.UtcNow;
        await _dbContext.SaveChangesAsync();

        _logger.LogInformation("Updated preferences for user {UserUuid}", userUuid);
        return MapToDto(entity);
    }

    private static NotificationPreferenceDto MapToDto(NotificationPreference entity) => new(
        entity.PreferenceUuid,
        entity.UserUuid,
        entity.PushEnabled,
        entity.EmailEnabled,
        entity.SmsEnabled,
        entity.RideUpdatesEnabled,
        entity.PaymentUpdatesEnabled,
        entity.PromotionalEnabled,
        entity.SecurityAlertsEnabled,
        entity.QuietHoursEnabled,
        entity.QuietHoursStart ?? new TimeSpan(22, 0, 0),
        entity.QuietHoursEnd ?? new TimeSpan(7, 0, 0),
        entity.PreferredLocale
    );
}
