using System.Collections.Concurrent;
using Microsoft.EntityFrameworkCore;
using DriverMatchingService.Configuration;
using DriverMatchingService.Data;
using DriverMatchingService.DTOs.Request;
using DriverMatchingService.DTOs.Response;
using DriverMatchingService.Entities;
using DriverMatchingService.Exceptions;

namespace DriverMatchingService.Services;

public class DriverLocationService : IDriverLocationService
{
    private readonly LexorDbContext _dbContext;
    private readonly ILogger<DriverLocationService> _logger;
    private readonly ConcurrentDictionary<long, DriverLocation> _locationCache = new();

    public DriverLocationService(
        LexorDbContext dbContext,
        ILogger<DriverLocationService> logger)
    {
        _dbContext = dbContext;
        _logger = logger;
    }

    public async Task<LocationUpdateResponse> UpdateDriverLocationAsync(string driverUuid, LocationUpdateRequestDto request)
    {
        var driver = await _dbContext.DriverProfiles
            .Include(dp => dp.User)
            .FirstOrDefaultAsync(dp => dp.User != null && dp.User.Uuid == driverUuid);

        if (driver == null)
        {
            throw new ResourceNotFoundException($"Driver profile not found for user: {driverUuid}");
        }

        // Validate coordinates
        if (request.Latitude < -90.0m || request.Latitude > 90.0m)
        {
            throw new BadRequestException("Latitude must be between -90.0 and 90.0 degrees");
        }
        if (request.Longitude < -180.0m || request.Longitude > 180.0m)
        {
            throw new BadRequestException("Longitude must be between -180.0 and 180.0 degrees");
        }

        var recordedAt = request.Timestamp ?? DateTime.UtcNow;

        if (recordedAt > DateTime.UtcNow.AddSeconds(60))
        {
            throw new BadRequestException("Location timestamp cannot be in the future");
        }

        var locationRecord = new DriverLocation
        {
            DriverId = driver.Id,
            Latitude = request.Latitude,
            Longitude = request.Longitude,
            Heading = request.Heading,
            SpeedKmh = request.SpeedKmh,
            RecordedAt = recordedAt
        };

        _dbContext.DriverLocations.Add(locationRecord);
        await _dbContext.SaveChangesAsync();

        _locationCache[driver.Id] = locationRecord;

        _logger.LogDebug("Ingested location for driver [{DriverId}] at lat={Lat}, lon={Lon}, recordedAt={RecordedAt}",
            driver.Id, locationRecord.Latitude, locationRecord.Longitude, recordedAt);

        return new LocationUpdateResponse
        {
            DriverUuid = driverUuid,
            Latitude = locationRecord.Latitude,
            Longitude = locationRecord.Longitude,
            Heading = locationRecord.Heading,
            SpeedKmh = locationRecord.SpeedKmh,
            RecordedAt = locationRecord.RecordedAt,
            Status = "SUCCESS"
        };
    }

    public async Task<DriverLocation?> GetLatestLocationAsync(long driverId)
    {
        if (_locationCache.TryGetValue(driverId, out var cached))
        {
            return cached;
        }

        var fromDb = await _dbContext.DriverLocations
            .Where(dl => dl.DriverId == driverId)
            .OrderByDescending(dl => dl.RecordedAt)
            .FirstOrDefaultAsync();

        if (fromDb != null)
        {
            _locationCache[driverId] = fromDb;
        }

        return fromDb;
    }

    public bool IsLocationFresh(DriverLocation? location, long maxAgeSeconds)
    {
        if (location == null)
        {
            return false;
        }

        var age = DateTime.UtcNow - location.RecordedAt;
        return age.TotalSeconds >= -60 && age.TotalSeconds <= maxAgeSeconds;
    }
}
