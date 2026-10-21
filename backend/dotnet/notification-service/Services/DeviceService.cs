using Microsoft.EntityFrameworkCore;
using NotificationService.Data;
using NotificationService.Domain.Entities;
using NotificationService.DTOs;
using NotificationService.Exceptions;

namespace NotificationService.Services;

public interface IDeviceService
{
    Task<DeviceDto> RegisterDeviceAsync(string userUuid, DeviceRegistrationRequest request);
    Task<List<DeviceDto>> ListUserDevicesAsync(string userUuid);
    Task<List<NotificationDevice>> GetActiveDeviceEntitiesAsync(string userUuid);
    Task DeactivateDeviceAsync(string userUuid, string deviceUuid);
    Task DeactivateInvalidTokenAsync(string fcmToken);
}

public class DeviceService : IDeviceService
{
    private readonly NotificationDbContext _dbContext;
    private readonly ILogger<DeviceService> _logger;

    public DeviceService(NotificationDbContext dbContext, ILogger<DeviceService> logger)
    {
        _dbContext = dbContext;
        _logger = logger;
    }

    public async Task<DeviceDto> RegisterDeviceAsync(string userUuid, DeviceRegistrationRequest request)
    {
        var token = request.FcmToken.Trim();

        var existing = await _dbContext.NotificationDevices.FirstOrDefaultAsync(d => d.FcmToken == token);

        NotificationDevice entity;
        if (existing != null)
        {
            entity = existing;
            entity.UserUuid = userUuid;
            entity.Platform = request.Platform;
            entity.DeviceModel = request.DeviceModel;
            entity.OsVersion = request.OsVersion;
            entity.AppVersion = request.AppVersion;
            entity.IsActive = true;
            entity.LastActiveAt = DateTime.UtcNow;
            entity.UpdatedAt = DateTime.UtcNow;
        }
        else
        {
            entity = new NotificationDevice
            {
                DeviceUuid = Guid.NewGuid().ToString(),
                UserUuid = userUuid,
                Platform = request.Platform,
                FcmToken = token,
                DeviceModel = request.DeviceModel,
                OsVersion = request.OsVersion,
                AppVersion = request.AppVersion,
                IsActive = true,
                LastActiveAt = DateTime.UtcNow,
                CreatedAt = DateTime.UtcNow,
                UpdatedAt = DateTime.UtcNow
            };
            _dbContext.NotificationDevices.Add(entity);
        }

        await _dbContext.SaveChangesAsync();
        _logger.LogInformation("Registered device {DeviceUuid} for user {UserUuid}", entity.DeviceUuid, userUuid);
        return MapToDto(entity);
    }

    public async Task<List<DeviceDto>> ListUserDevicesAsync(string userUuid)
    {
        var list = await _dbContext.NotificationDevices
            .Where(d => d.UserUuid == userUuid && d.IsActive)
            .ToListAsync();

        return list.Select(MapToDto).ToList();
    }

    public async Task<List<NotificationDevice>> GetActiveDeviceEntitiesAsync(string userUuid)
    {
        return await _dbContext.NotificationDevices
            .Where(d => d.UserUuid == userUuid && d.IsActive)
            .ToListAsync();
    }

    public async Task DeactivateDeviceAsync(string userUuid, string deviceUuid)
    {
        var device = await _dbContext.NotificationDevices
            .FirstOrDefaultAsync(d => d.DeviceUuid == deviceUuid);

        if (device == null)
        {
            throw new ResourceNotFoundException($"Device not found: {deviceUuid}");
        }

        if (device.UserUuid != userUuid)
        {
            throw new ForbiddenException("Cannot deactivate device belonging to another user");
        }

        device.IsActive = false;
        device.UpdatedAt = DateTime.UtcNow;
        await _dbContext.SaveChangesAsync();
        _logger.LogInformation("Deactivated device {DeviceUuid} for user {UserUuid}", deviceUuid, userUuid);
    }

    public async Task DeactivateInvalidTokenAsync(string fcmToken)
    {
        var device = await _dbContext.NotificationDevices.FirstOrDefaultAsync(d => d.FcmToken == fcmToken);
        if (device != null)
        {
            device.IsActive = false;
            device.UpdatedAt = DateTime.UtcNow;
            await _dbContext.SaveChangesAsync();
            _logger.LogWarning("Auto-deactivated invalid device token {DeviceUuid}", device.DeviceUuid);
        }
    }

    private static DeviceDto MapToDto(NotificationDevice entity)
    {
        var token = entity.FcmToken;
        var masked = !string.IsNullOrEmpty(token) && token.Length > 8
            ? $"{token[..4]}...{token[^4..]}"
            : "***";

        return new DeviceDto(
            entity.DeviceUuid,
            entity.UserUuid,
            entity.Platform,
            masked,
            entity.DeviceModel,
            entity.OsVersion,
            entity.AppVersion,
            entity.IsActive,
            entity.LastActiveAt,
            entity.CreatedAt
        );
    }
}
