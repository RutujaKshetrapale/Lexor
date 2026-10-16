using System.Security.Claims;
using Microsoft.EntityFrameworkCore;
using RideRequestService.Data;
using RideRequestService.DTOs.Request;
using RideRequestService.DTOs.Response;
using RideRequestService.Entities;
using RideRequestService.Enums;
using RideRequestService.Exceptions;

namespace RideRequestService.Services;

public class RideRequestService : IRideRequestService
{
    private readonly LexorDbContext _dbContext;
    private readonly IFareEstimationService _fareEstimationService;
    private readonly IRideTypeClient _rideTypeClient;
    private readonly ILogger<RideRequestService> _logger;

    public RideRequestService(
        LexorDbContext dbContext,
        IFareEstimationService fareEstimationService,
        IRideTypeClient rideTypeClient,
        ILogger<RideRequestService> logger)
    {
        _dbContext = dbContext;
        _fareEstimationService = fareEstimationService;
        _rideTypeClient = rideTypeClient;
        _logger = logger;
    }

    public async Task<RideRequestResponse> CreateRideRequestAsync(ClaimsPrincipal riderPrincipal, CreateRideRequestDto request, string? bearerToken)
    {
        EnsureRiderRole(riderPrincipal);

        var user = await GetAuthenticatedUserAsync(riderPrincipal);
        var riderProfile = await GetOrCreateRiderProfileAsync(user);

        // Resolve and validate Ride Type category
        var rideType = await ResolveRideTypeAsync(request, bearerToken);

        if (!rideType.IsActive)
        {
            throw new BadRequestException($"Ride category '{rideType.Name}' is currently disabled and unavailable for booking.");
        }

        // Calculate route distance, duration, and estimated fare
        var fareEstimate = _fareEstimationService.EstimateFare(
            request.PickupLatitude, request.PickupLongitude,
            request.DropLatitude, request.DropLongitude,
            rideType
        );

        var rideRequest = new RideRequest
        {
            Uuid = Guid.NewGuid().ToString(),
            RiderId = riderProfile.Id,
            Rider = riderProfile,
            RideTypeId = rideType.Id,
            RideType = rideType,
            PickupAddress = request.PickupAddress.Trim(),
            PickupLatitude = request.PickupLatitude!.Value,
            PickupLongitude = request.PickupLongitude!.Value,
            DropAddress = request.DropAddress.Trim(),
            DropLatitude = request.DropLatitude!.Value,
            DropLongitude = request.DropLongitude!.Value,
            EstimatedDistanceKm = fareEstimate.DistanceKm,
            EstimatedDurationMinutes = fareEstimate.DurationMinutes,
            EstimatedFare = fareEstimate.FinalEstimatedFare,
            CouponId = request.CouponId,
            Status = RideRequestStatus.REQUESTED,
            RequestedAt = DateTime.UtcNow,
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        _dbContext.RideRequests.Add(rideRequest);
        await _dbContext.SaveChangesAsync();

        _logger.LogInformation("Created ride request [{Uuid}] for rider [{Email}] with category [{Code}]",
            rideRequest.Uuid, user.Email, rideType.Code);

        return MapToResponse(rideRequest);
    }

    public async Task<PageResponse<RideRequestResponse>> GetMyRideRequestsAsync(ClaimsPrincipal riderPrincipal, int page, int size, RideRequestStatus? status)
    {
        var user = await GetAuthenticatedUserAsync(riderPrincipal);

        var riderProfile = await _dbContext.RiderProfiles
            .FirstOrDefaultAsync(rp => rp.UserId == user.Id);

        if (riderProfile == null)
        {
            throw new ResourceNotFoundException($"Rider profile not found for user: {user.Email}");
        }

        var query = _dbContext.RideRequests
            .AsNoTracking()
            .Include(r => r.Rider)
                .ThenInclude(rp => rp.User)
            .Include(r => r.RideType)
            .Where(r => r.RiderId == riderProfile.Id);

        if (status.HasValue)
        {
            query = query.Where(r => r.Status == status.Value);
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

    public async Task<RideRequestResponse> GetMyRideRequestByUuidAsync(ClaimsPrincipal riderPrincipal, string requestUuid)
    {
        var user = await GetAuthenticatedUserAsync(riderPrincipal);

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

        // Enforce ownership: Rider can only view their own request
        if (rideRequest.Rider.UserId != user.Id)
        {
            _logger.LogWarning("Rider [{Email}] attempted unauthorized access to ride request [{RequestUuid}] owned by user ID [{OwnerId}]",
                user.Email, requestUuid, rideRequest.Rider.UserId);
            throw new ForbiddenException("You do not have permission to view this ride request");
        }

        return MapToResponse(rideRequest);
    }

    public async Task<RideRequestResponse> CancelRideRequestAsync(ClaimsPrincipal riderPrincipal, string requestUuid, CancelRideRequestDto? request)
    {
        var user = await GetAuthenticatedUserAsync(riderPrincipal);

        var rideRequest = await _dbContext.RideRequests
            .Include(r => r.Rider)
                .ThenInclude(rp => rp.User)
            .Include(r => r.RideType)
            .FirstOrDefaultAsync(r => r.Uuid == requestUuid);

        if (rideRequest == null)
        {
            throw new ResourceNotFoundException($"Ride request not found with UUID: {requestUuid}");
        }

        // Enforce ownership: Rider can only cancel their own request
        if (rideRequest.Rider.UserId != user.Id)
        {
            _logger.LogWarning("Rider [{Email}] attempted unauthorized cancellation of ride request [{RequestUuid}]",
                user.Email, requestUuid);
            throw new ForbiddenException("You do not have permission to cancel this ride request");
        }

        // Validate state transitions
        if (rideRequest.Status == RideRequestStatus.CANCELLED)
        {
            throw new ConflictException("Ride request is already cancelled.");
        }
        if (rideRequest.Status == RideRequestStatus.MATCHED)
        {
            throw new ConflictException("Ride request has already been matched with a driver and cannot be cancelled through this endpoint.");
        }
        if (rideRequest.Status == RideRequestStatus.EXPIRED)
        {
            throw new ConflictException("Ride request has expired and cannot be cancelled.");
        }
        if (!rideRequest.Status.CanCancel())
        {
            throw new ConflictException($"Ride request cannot be cancelled in status: {rideRequest.Status}");
        }

        rideRequest.Status = RideRequestStatus.CANCELLED;
        rideRequest.UpdatedAt = DateTime.UtcNow;

        await _dbContext.SaveChangesAsync();

        _logger.LogInformation("Rider [{Email}] successfully cancelled ride request [{RequestUuid}] (Reason: {Reason})",
            user.Email, requestUuid, request?.Reason ?? "None provided");

        return MapToResponse(rideRequest);
    }

    private void EnsureRiderRole(ClaimsPrincipal principal)
    {
        var roles = principal.FindAll(c => c.Type == ClaimTypes.Role || c.Type == "role" || c.Type == "roles")
            .Select(c => c.Value);

        bool isRider = roles.Any(r => r.Equals("RIDER", StringComparison.OrdinalIgnoreCase) ||
                                     r.Equals("ROLE_RIDER", StringComparison.OrdinalIgnoreCase));

        if (!isRider)
        {
            throw new ForbiddenException("Only authenticated users with role RIDER are permitted to create ride requests");
        }
    }

    private async Task<User> GetAuthenticatedUserAsync(ClaimsPrincipal principal)
    {
        var uuid = principal.FindFirstValue("uuid")
                   ?? principal.FindFirstValue(ClaimTypes.NameIdentifier)
                   ?? principal.FindFirstValue("sub");

        var userIdClaim = principal.FindFirstValue("userId");
        long? userId = long.TryParse(userIdClaim, out var parsedId) ? parsedId : null;

        var email = principal.FindFirstValue(ClaimTypes.Email)
                    ?? principal.FindFirstValue("email");

        var user = await _dbContext.Users.FirstOrDefaultAsync(u =>
            (uuid != null && u.Uuid == uuid) ||
            (userId.HasValue && u.Id == userId.Value) ||
            (email != null && u.Email == email));

        if (user == null)
        {
            throw new ResourceNotFoundException($"User not found with identifier: {uuid ?? userIdClaim ?? email ?? "unknown"}");
        }

        return user;
    }

    private async Task<RiderProfile> GetOrCreateRiderProfileAsync(User user)
    {
        var profile = await _dbContext.RiderProfiles.FirstOrDefaultAsync(rp => rp.UserId == user.Id);
        if (profile != null)
        {
            profile.User = user;
            return profile;
        }

        profile = new RiderProfile
        {
            UserId = user.Id,
            User = user,
            Rating = 5.00m,
            TotalRides = 0,
            PreferredPaymentMethod = "UPI",
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        _dbContext.RiderProfiles.Add(profile);
        await _dbContext.SaveChangesAsync();
        return profile;
    }

    private async Task<RideType> ResolveRideTypeAsync(CreateRideRequestDto request, string? bearerToken)
    {
        // 1. Attempt lookup by rideTypeUuid via RideTypeClient
        if (!string.IsNullOrWhiteSpace(request.RideTypeUuid))
        {
            try
            {
                var externalDto = await _rideTypeClient.GetRideTypeByUuidAsync(request.RideTypeUuid.Trim(), bearerToken);
                if (externalDto != null)
                {
                    var code = externalDto.Code;
                    var localRideType = await _dbContext.RideTypes.FirstOrDefaultAsync(rt => rt.Code == code);
                    if (localRideType == null)
                    {
                        throw new ResourceNotFoundException($"Ride category '{code}' was verified via service but not found in platform database");
                    }
                    return localRideType;
                }
            }
            catch (ResourceNotFoundException)
            {
                throw;
            }
            catch (BadRequestException)
            {
                throw;
            }
            catch (Exception ex)
            {
                _logger.LogWarning("Could not resolve ride type via client ({Message}): falling back to local database lookup", ex.Message);
            }
        }

        // 2. Direct lookup by category code
        if (!string.IsNullOrWhiteSpace(request.RideTypeCode))
        {
            var code = request.RideTypeCode.Trim().ToUpperInvariant();
            var localByCode = await _dbContext.RideTypes.FirstOrDefaultAsync(rt => rt.Code == code);
            if (localByCode == null)
            {
                throw new ResourceNotFoundException($"Ride category with code '{request.RideTypeCode}' not found");
            }
            return localByCode;
        }

        // 3. Fallback: If UUID was supplied and happens to match a code or lookup
        if (!string.IsNullOrWhiteSpace(request.RideTypeUuid))
        {
            var fallbackCode = request.RideTypeUuid.Trim().ToUpperInvariant();
            var localByCode = await _dbContext.RideTypes.FirstOrDefaultAsync(rt => rt.Code == fallbackCode);
            if (localByCode != null)
            {
                return localByCode;
            }
        }

        throw new BadRequestException("Must specify a valid 'rideTypeUuid' or 'rideTypeCode' (e.g. 'GO', 'XL', 'PREMIER', 'GREEN')");
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
