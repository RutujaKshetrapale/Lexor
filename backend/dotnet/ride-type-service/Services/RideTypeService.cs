using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;
using RideTypeService.Data;
using RideTypeService.DTOs.Request;
using RideTypeService.DTOs.Response;
using RideTypeService.Entities;
using RideTypeService.Exceptions;

namespace RideTypeService.Services;

public class RideTypeService : IRideTypeService
{
    private readonly LexorDbContext _dbContext;
    private readonly ILogger<RideTypeService> _logger;

    public RideTypeService(LexorDbContext dbContext, ILogger<RideTypeService> logger)
    {
        _dbContext = dbContext;
        _logger = logger;
    }

    public async Task<List<RideTypeResponse>> GetEnabledRideTypesAsync()
    {
        _logger.LogDebug("Fetching all enabled ride types for rider catalogue");

        var rideTypes = await _dbContext.RideTypes
            .AsNoTracking()
            .Where(r => r.IsActive)
            .OrderBy(r => r.Id)
            .ToListAsync();

        return rideTypes.Select(MapToResponse).ToList();
    }

    public async Task<RideTypeResponse> GetEnabledRideTypeByUuidAsync(string uuid)
    {
        _logger.LogDebug("Fetching enabled ride type with UUID: {Uuid}", uuid);

        var rideType = await _dbContext.RideTypes
            .AsNoTracking()
            .FirstOrDefaultAsync(r => r.Uuid == uuid && r.IsActive);

        if (rideType == null)
        {
            throw new ResourceNotFoundException($"Enabled ride type not found with UUID: {uuid}");
        }

        return MapToResponse(rideType);
    }

    public async Task<List<RideTypeResponse>> GetAllRideTypesAdminAsync()
    {
        _logger.LogDebug("Fetching all ride types (enabled and disabled) for administrator");

        var rideTypes = await _dbContext.RideTypes
            .AsNoTracking()
            .OrderBy(r => r.Id)
            .ToListAsync();

        return rideTypes.Select(MapToResponse).ToList();
    }

    public async Task<RideTypeResponse> GetRideTypeByUuidAdminAsync(string uuid)
    {
        _logger.LogDebug("Fetching administrative ride type details with UUID: {Uuid}", uuid);

        var rideType = await _dbContext.RideTypes
            .AsNoTracking()
            .FirstOrDefaultAsync(r => r.Uuid == uuid);

        if (rideType == null)
        {
            throw new ResourceNotFoundException($"Ride type not found with UUID: {uuid}");
        }

        return MapToResponse(rideType);
    }

    public async Task<RideTypeResponse> CreateRideTypeAsync(CreateRideTypeRequest request)
    {
        _logger.LogInformation("Admin creating new ride type with name: '{Name}', code: '{Code}'", request.Name, request.Code);

        var normalizedCode = request.Code.Trim().ToUpperInvariant();
        var normalizedName = request.Name.Trim();

        ValidateFareConstraints(request.BaseFare, request.MinimumFare, request.CancellationFee);

        if (request.Capacity < 1 || request.Capacity > 20)
        {
            throw new BadRequestException("Capacity must be between 1 and 20");
        }

        if (await _dbContext.RideTypes.AnyAsync(r => r.Code.ToUpper() == normalizedCode))
        {
            throw new DuplicateResourceException($"Ride type code already exists: {normalizedCode}");
        }

        if (await _dbContext.RideTypes.AnyAsync(r => r.Name.ToUpper() == normalizedName.ToUpper()))
        {
            throw new DuplicateResourceException($"Ride type name already exists: {normalizedName}");
        }

        var cancellationFee = request.CancellationFee ?? 0.00m;
        var isActive = request.IsActive ?? true;

        var rideType = new RideType
        {
            Uuid = Guid.NewGuid().ToString(),
            Name = normalizedName,
            Code = normalizedCode,
            Description = string.IsNullOrWhiteSpace(request.Description) ? null : request.Description.Trim(),
            BaseFare = request.BaseFare,
            PerKmRate = request.PerKmRate,
            PerMinuteRate = request.PerMinuteRate,
            MinimumFare = request.MinimumFare,
            CancellationFee = cancellationFee,
            Capacity = request.Capacity,
            IconUrl = string.IsNullOrWhiteSpace(request.IconUrl) ? null : request.IconUrl.Trim(),
            IsActive = isActive,
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        _dbContext.RideTypes.Add(rideType);
        await _dbContext.SaveChangesAsync();

        _logger.LogInformation("Successfully created ride type ID: {Id}, UUID: {Uuid}", rideType.Id, rideType.Uuid);
        return MapToResponse(rideType);
    }

    public async Task<RideTypeResponse> UpdateRideTypeAsync(string uuid, UpdateRideTypeRequest request)
    {
        _logger.LogInformation("Admin updating ride type UUID: {Uuid}", uuid);

        var rideType = await _dbContext.RideTypes.FirstOrDefaultAsync(r => r.Uuid == uuid);
        if (rideType == null)
        {
            throw new ResourceNotFoundException($"Ride type not found with UUID: {uuid}");
        }

        var normalizedName = request.Name.Trim();
        if (await _dbContext.RideTypes.AnyAsync(r => r.Name.ToUpper() == normalizedName.ToUpper() && r.Uuid != uuid))
        {
            throw new DuplicateResourceException($"Ride type name already exists: {normalizedName}");
        }

        if (!string.IsNullOrWhiteSpace(request.Code))
        {
            var normalizedCode = request.Code.Trim().ToUpperInvariant();
            if (await _dbContext.RideTypes.AnyAsync(r => r.Code.ToUpper() == normalizedCode && r.Uuid != uuid))
            {
                throw new DuplicateResourceException($"Ride type code already exists: {normalizedCode}");
            }
            rideType.Code = normalizedCode;
        }

        ValidateFareConstraints(request.BaseFare, request.MinimumFare, request.CancellationFee);

        if (request.Capacity < 1 || request.Capacity > 20)
        {
            throw new BadRequestException("Capacity must be between 1 and 20");
        }

        var cancellationFee = request.CancellationFee ?? 0.00m;

        rideType.Name = normalizedName;
        rideType.Description = string.IsNullOrWhiteSpace(request.Description) ? null : request.Description.Trim();
        rideType.BaseFare = request.BaseFare;
        rideType.PerKmRate = request.PerKmRate;
        rideType.PerMinuteRate = request.PerMinuteRate;
        rideType.MinimumFare = request.MinimumFare;
        rideType.CancellationFee = cancellationFee;
        rideType.Capacity = request.Capacity;
        rideType.IconUrl = string.IsNullOrWhiteSpace(request.IconUrl) ? null : request.IconUrl.Trim();
        rideType.UpdatedAt = DateTime.UtcNow;

        await _dbContext.SaveChangesAsync();
        _logger.LogInformation("Successfully updated ride type UUID: {Uuid}", rideType.Uuid);

        return MapToResponse(rideType);
    }

    public async Task<RideTypeResponse> UpdateRideTypeStatusAsync(string uuid, UpdateRideTypeStatusRequest request)
    {
        _logger.LogInformation("Admin setting ride type UUID: {Uuid} status to: {Status}", uuid, request.IsActive);

        var rideType = await _dbContext.RideTypes.FirstOrDefaultAsync(r => r.Uuid == uuid);
        if (rideType == null)
        {
            throw new ResourceNotFoundException($"Ride type not found with UUID: {uuid}");
        }

        var targetStatus = request.IsActive ?? true;

        if (!targetStatus && rideType.IsActive)
        {
            var activeCount = await _dbContext.RideTypes.CountAsync(r => r.IsActive);
            if (activeCount <= 1)
            {
                throw new BadRequestException("Cannot disable the last active ride type on the platform");
            }
        }

        rideType.IsActive = targetStatus;
        rideType.UpdatedAt = DateTime.UtcNow;

        await _dbContext.SaveChangesAsync();
        _logger.LogInformation("Successfully updated ride type UUID: {Uuid} isActive: {IsActive}", rideType.Uuid, rideType.IsActive);

        return MapToResponse(rideType);
    }

    public async Task DeleteRideTypeSafelyAsync(string uuid)
    {
        _logger.LogWarning("Attempt to delete ride type UUID: {Uuid}", uuid);

        var rideType = await _dbContext.RideTypes.FirstOrDefaultAsync(r => r.Uuid == uuid);
        if (rideType == null)
        {
            throw new ResourceNotFoundException($"Ride type not found with UUID: {uuid}");
        }

        // Hard deletion is prohibited to preserve referential integrity with bookings, rides, and vehicles
        throw new BadRequestException("Hard deletion of ride categories is forbidden to preserve historical records and referential integrity. Please disable the ride type instead.");
    }

    private static void ValidateFareConstraints(decimal baseFare, decimal minimumFare, decimal? cancellationFee)
    {
        if (baseFare < 0)
        {
            throw new BadRequestException("Base fare cannot be negative");
        }
        if (minimumFare < 0)
        {
            throw new BadRequestException("Minimum fare cannot be negative");
        }
        if (minimumFare < baseFare)
        {
            throw new BadRequestException($"Minimum fare ({minimumFare}) cannot be less than base fare ({baseFare})");
        }
        if (cancellationFee.HasValue && cancellationFee.Value < 0)
        {
            throw new BadRequestException("Cancellation fee cannot be negative");
        }
    }

    private static RideTypeResponse MapToResponse(RideType entity)
    {
        return new RideTypeResponse
        {
            Uuid = entity.Uuid,
            Name = entity.Name,
            Code = entity.Code,
            Description = entity.Description,
            BaseFare = entity.BaseFare,
            PerKmRate = entity.PerKmRate,
            PerMinuteRate = entity.PerMinuteRate,
            MinimumFare = entity.MinimumFare,
            CancellationFee = entity.CancellationFee,
            Capacity = entity.Capacity,
            IconUrl = entity.IconUrl,
            IsActive = entity.IsActive,
            CreatedAt = entity.CreatedAt,
            UpdatedAt = entity.UpdatedAt
        };
    }
}
