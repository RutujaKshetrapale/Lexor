using Microsoft.EntityFrameworkCore;
using RideRequestService.Data;
using RideRequestService.DTOs.Response;
using RideRequestService.Entities;
using RideRequestService.Enums;
using RideRequestService.Exceptions;

namespace RideRequestService.Services;

public class AdminRideRequestService : IAdminRideRequestService
{
    private readonly LexorDbContext _dbContext;

    public AdminRideRequestService(LexorDbContext dbContext)
    {
        _dbContext = dbContext;
    }

    public async Task<PageResponse<RideRequestResponse>> GetAllRideRequestsAsync(int page, int size, RideRequestStatus? status, long? riderId)
    {
        var query = _dbContext.RideRequests
            .AsNoTracking()
            .Include(r => r.Rider)
                .ThenInclude(rp => rp.User)
            .Include(r => r.RideType)
            .AsQueryable();

        if (status.HasValue)
        {
            query = query.Where(r => r.Status == status.Value);
        }

        if (riderId.HasValue)
        {
            query = query.Where(r => r.RiderId == riderId.Value);
        }

        var totalElements = await query.LongCountAsync();

        var content = await query
            .OrderByDescending(r => r.RequestedAt)
            .Skip(page * size)
            .Take(size)
            .Select(r => MapToResponse(r))
            .ToListAsync();

        return PageResponse<RideRequestResponse>.Create(content, page, size, totalElements);
    }

    public async Task<RideRequestResponse> GetRideRequestByUuidAsync(string requestUuid)
    {
        var rideRequest = await _dbContext.RideRequests
            .AsNoTracking()
            .Include(r => r.Rider)
                .ThenInclude(rp => rp.User)
            .Include(r => r.RideType)
            .FirstOrDefaultAsync(r => r.Uuid == requestUuid);

        if (rideRequest == null)
        {
            throw new ResourceNotFoundException($"Ride request not found with UUID: {requestUuid}");
        }

        return MapToResponse(rideRequest);
    }

    private static RideRequestResponse MapToResponse(RideRequest request)
    {
        var user = request.Rider?.User;
        var rideType = request.RideType;

        return new RideRequestResponse
        {
            RequestUuid = request.Uuid,
            RiderUuid = user?.Uuid ?? string.Empty,
            RiderName = user != null ? $"{user.FirstName} {user.LastName}".Trim() : string.Empty,
            RiderEmail = user?.Email ?? string.Empty,
            PickupAddress = request.PickupAddress,
            PickupLatitude = request.PickupLatitude,
            PickupLongitude = request.PickupLongitude,
            DropAddress = request.DropAddress,
            DropLatitude = request.DropLatitude,
            DropLongitude = request.DropLongitude,
            RideTypeCode = rideType?.Code ?? string.Empty,
            RideTypeName = rideType?.Name ?? string.Empty,
            EstimatedDistanceKm = request.EstimatedDistanceKm,
            EstimatedDurationMinutes = request.EstimatedDurationMinutes,
            EstimatedFare = request.EstimatedFare,
            Currency = "INR",
            Status = request.Status,
            RequestedAt = request.RequestedAt,
            CreatedAt = request.CreatedAt,
            UpdatedAt = request.UpdatedAt
        };
    }
}
