using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using DriverMatchingService.Configuration;
using DriverMatchingService.Data;
using DriverMatchingService.DTOs.Request;
using DriverMatchingService.DTOs.Response;
using DriverMatchingService.Entities;
using DriverMatchingService.Enums;
using DriverMatchingService.Events;
using DriverMatchingService.Exceptions;

namespace DriverMatchingService.Services;

public class DriverMatchingService : IDriverMatchingService
{
    private readonly LexorDbContext _dbContext;
    private readonly IDriverLocationService _driverLocationService;
    private readonly IScoringPolicyService _scoringPolicyService;
    private readonly IEventPublisher _eventPublisher;
    private readonly MatchingSettings _matchingSettings;
    private readonly ILogger<DriverMatchingService> _logger;

    private static readonly object ReservationLock = new();

    public DriverMatchingService(
        LexorDbContext dbContext,
        IDriverLocationService driverLocationService,
        IScoringPolicyService scoringPolicyService,
        IEventPublisher eventPublisher,
        IOptions<MatchingSettings> matchingSettings,
        ILogger<DriverMatchingService> logger)
    {
        _dbContext = dbContext;
        _driverLocationService = driverLocationService;
        _scoringPolicyService = scoringPolicyService;
        _eventPublisher = eventPublisher;
        _matchingSettings = matchingSettings.Value;
        _logger = logger;
    }

    public async Task<MatchingAttemptResponse> InitiateMatchingAsync(string requestUuid, string? correlationId)
    {
        var rideRequest = await GetRideRequestOrThrowAsync(requestUuid);

        // Idempotency: If request is already matched, return existing match attempt
        var latestAttempt = await _dbContext.MatchingAttempts
            .Include(ma => ma.SelectedDriver)
                .ThenInclude(d => d!.User)
            .Include(ma => ma.SelectedVehicle)
            .Where(ma => ma.RideRequestUuid == requestUuid)
            .OrderByDescending(ma => ma.AttemptNumber)
            .FirstOrDefaultAsync();

        if (latestAttempt != null && latestAttempt.Status == MatchingStatus.MATCHED)
        {
            _logger.LogInformation("Ride request [{RequestUuid}] is already matched. Returning idempotent attempt [{AttemptUuid}]",
                requestUuid, latestAttempt.AttemptUuid);
            return MapToResponse(latestAttempt);
        }

        if (!rideRequest.IsEligibleForMatching())
        {
            throw new ConflictException($"Ride request with UUID {requestUuid} is in status {rideRequest.Status} and cannot be matched.");
        }

        int previousAttemptsCount = await _dbContext.MatchingAttempts
            .CountAsync(ma => ma.RideRequestUuid == requestUuid);
        int attemptNumber = previousAttemptsCount + 1;

        return await ExecuteMatchingAttemptAsync(rideRequest, attemptNumber, correlationId);
    }

    public async Task<MatchingAttemptResponse> GetMatchingStatusAsync(string requestUuid)
    {
        var attempt = await _dbContext.MatchingAttempts
            .Include(ma => ma.SelectedDriver)
                .ThenInclude(d => d!.User)
            .Include(ma => ma.SelectedVehicle)
            .Where(ma => ma.RideRequestUuid == requestUuid)
            .OrderByDescending(ma => ma.AttemptNumber)
            .FirstOrDefaultAsync();

        if (attempt == null)
        {
            throw new ResourceNotFoundException($"No matching attempt found for ride request UUID: {requestUuid}");
        }

        return MapToResponse(attempt);
    }

    public async Task<MatchingAttemptResponse> RetryMatchingAsync(string requestUuid, MatchingRetryRequestDto retryRequest)
    {
        var rideRequest = await GetRideRequestOrThrowAsync(requestUuid);

        if (!rideRequest.IsEligibleForMatching())
        {
            throw new ConflictException($"Ride request with UUID {requestUuid} cannot be retried in status: {rideRequest.Status}");
        }

        var latestAttempt = await _dbContext.MatchingAttempts
            .Where(ma => ma.RideRequestUuid == requestUuid)
            .OrderByDescending(ma => ma.AttemptNumber)
            .FirstOrDefaultAsync();

        if (latestAttempt != null && latestAttempt.Status == MatchingStatus.MATCHED)
        {
            throw new ConflictException("Ride request is already matched and cannot be retried.");
        }

        int previousAttemptsCount = await _dbContext.MatchingAttempts
            .CountAsync(ma => ma.RideRequestUuid == requestUuid);
        int attemptNumber = previousAttemptsCount + 1;

        string correlationId = Guid.NewGuid().ToString();
        return await ExecuteMatchingAttemptAsync(rideRequest, attemptNumber, correlationId);
    }

    private async Task<MatchingAttemptResponse> ExecuteMatchingAttemptAsync(
        RideRequest rideRequest,
        int attemptNumber,
        string? correlationId)
    {
        var now = DateTime.UtcNow;
        var attemptUuid = Guid.NewGuid().ToString();

        PublishEvent("DRIVER_MATCHING_REQUESTED", rideRequest.Uuid, correlationId, new Dictionary<string, object>
        {
            ["attemptUuid"] = attemptUuid,
            ["attemptNumber"] = attemptNumber,
            ["pickupLat"] = rideRequest.PickupLatitude,
            ["pickupLon"] = rideRequest.PickupLongitude
        });

        // 1. Retrieve candidates: drivers who are ONLINE and VERIFIED
        var onlineDrivers = await _dbContext.DriverProfiles
            .Include(dp => dp.User)
            .Where(dp => dp.Status == DriverStatus.ONLINE
                      && dp.IsVerified
                      && dp.OnboardingStatus == OnboardingStatus.VERIFIED
                      && dp.BackgroundCheckStatus == BackgroundCheckStatus.PASSED
                      && dp.User != null && dp.User.Status == UserStatus.ACTIVE)
            .ToListAsync();

        string? requestedCategoryCode = rideRequest.RideType?.Code;

        var candidateEvaluations = new List<CandidateEvaluation>();

        foreach (var driver in onlineDrivers)
        {
            // Check if driver has an active reservation
            bool hasActiveReservation = await _dbContext.DriverReservations
                .AnyAsync(r => r.DriverId == driver.Id &&
                              (r.Status == ReservationStatus.CONFIRMED ||
                              (r.Status == ReservationStatus.RESERVED && r.ExpiresAt > now)));

            if (hasActiveReservation)
            {
                continue;
            }

            // Find driver's active vehicle assignment
            var assignment = await _dbContext.DriverVehicleAssignments
                .Include(dva => dva.Vehicle)
                .FirstOrDefaultAsync(dva => dva.DriverId == driver.Id && dva.Status == AssignmentStatus.ACTIVE);

            if (assignment?.Vehicle == null || !assignment.Vehicle.IsEligibleForMatching(requestedCategoryCode))
            {
                continue;
            }

            var vehicle = assignment.Vehicle;

            // Retrieve driver's latest GPS telemetry
            var location = await _driverLocationService.GetLatestLocationAsync(driver.Id);
            if (location == null)
            {
                continue;
            }

            if (!_driverLocationService.IsLocationFresh(location, _matchingSettings.MaxLocationAgeSeconds))
            {
                continue;
            }

            // Calculate distance to pickup
            double distanceKm = _scoringPolicyService.CalculateHaversineDistanceKm(
                (double)location.Latitude,
                (double)location.Longitude,
                (double)rideRequest.PickupLatitude,
                (double)rideRequest.PickupLongitude
            );

            if (distanceKm > _matchingSettings.MaxRadiusKm)
            {
                continue;
            }

            // Calculate deterministic score
            decimal matchScore = _scoringPolicyService.CalculateMatchScore(
                driver,
                distanceKm,
                _matchingSettings.MaxRadiusKm
            );

            candidateEvaluations.Add(new CandidateEvaluation(driver, vehicle, location, distanceKm, matchScore));
        }

        // Sort candidates deterministically: Highest score first, then shortest distance, then driver ID
        candidateEvaluations = candidateEvaluations
            .OrderByDescending(c => c.Score)
            .ThenBy(c => c.DistanceKm)
            .ThenBy(c => c.Driver.Id)
            .ToList();

        if (candidateEvaluations.Count == 0)
        {
            var failedAttempt = new MatchingAttempt
            {
                AttemptUuid = attemptUuid,
                RideRequestId = rideRequest.Id,
                RideRequestUuid = rideRequest.Uuid,
                Status = MatchingStatus.NO_DRIVERS_FOUND,
                AttemptNumber = attemptNumber,
                CandidatesEvaluated = 0,
                FailureReason = $"No eligible, active drivers found within {_matchingSettings.MaxRadiusKm} km",
                CreatedAt = DateTime.UtcNow,
                CompletedAt = DateTime.UtcNow
            };

            _dbContext.MatchingAttempts.Add(failedAttempt);
            await _dbContext.SaveChangesAsync();

            PublishEvent("DRIVER_MATCHING_FAILED", rideRequest.Uuid, correlationId, new Dictionary<string, object>
            {
                ["attemptUuid"] = attemptUuid,
                ["reason"] = "NO_DRIVERS_FOUND"
            });

            return MapToResponse(failedAttempt);
        }

        CandidateEvaluation? chosenCandidate = null;
        DriverReservation? reservation = null;

        lock (ReservationLock)
        {
            foreach (var candidate in candidateEvaluations)
            {
                bool isReserved = _dbContext.DriverReservations
                    .Any(r => r.DriverId == candidate.Driver.Id &&
                             (r.Status == ReservationStatus.CONFIRMED ||
                             (r.Status == ReservationStatus.RESERVED && r.ExpiresAt > DateTime.UtcNow)));

                if (isReserved)
                {
                    continue;
                }

                chosenCandidate = candidate;
                break;
            }

            if (chosenCandidate == null)
            {
                var failedAttempt = new MatchingAttempt
                {
                    AttemptUuid = attemptUuid,
                    RideRequestId = rideRequest.Id,
                    RideRequestUuid = rideRequest.Uuid,
                    Status = MatchingStatus.NO_DRIVERS_FOUND,
                    AttemptNumber = attemptNumber,
                    CandidatesEvaluated = candidateEvaluations.Count,
                    FailureReason = "All eligible candidates were claimed by concurrent requests",
                    CreatedAt = DateTime.UtcNow,
                    CompletedAt = DateTime.UtcNow
                };

                _dbContext.MatchingAttempts.Add(failedAttempt);
                _dbContext.SaveChanges();
                return MapToResponse(failedAttempt);
            }

            var successAttempt = new MatchingAttempt
            {
                AttemptUuid = attemptUuid,
                RideRequestId = rideRequest.Id,
                RideRequestUuid = rideRequest.Uuid,
                SelectedDriverId = chosenCandidate.Driver.Id,
                SelectedDriver = chosenCandidate.Driver,
                SelectedVehicleId = chosenCandidate.Vehicle.Id,
                SelectedVehicle = chosenCandidate.Vehicle,
                Status = MatchingStatus.MATCHED,
                AttemptNumber = attemptNumber,
                CandidatesEvaluated = candidateEvaluations.Count,
                DistanceKm = (decimal)Math.Round(chosenCandidate.DistanceKm, 2),
                MatchScore = chosenCandidate.Score,
                CreatedAt = DateTime.UtcNow,
                CompletedAt = DateTime.UtcNow
            };

            _dbContext.MatchingAttempts.Add(successAttempt);
            _dbContext.SaveChanges();

            var reservationExpiry = DateTime.UtcNow.AddSeconds(_matchingSettings.ReservationTtlSeconds);
            reservation = new DriverReservation
            {
                ReservationUuid = Guid.NewGuid().ToString(),
                DriverId = chosenCandidate.Driver.Id,
                RideRequestId = rideRequest.Id,
                MatchingAttemptId = successAttempt.Id,
                Status = ReservationStatus.RESERVED,
                ExpiresAt = reservationExpiry,
                CreatedAt = DateTime.UtcNow
            };

            _dbContext.DriverReservations.Add(reservation);
            _dbContext.SaveChanges();

            PublishEvent("DRIVER_ASSIGNMENT_RESERVED", rideRequest.Uuid, correlationId, new Dictionary<string, object>
            {
                ["attemptUuid"] = successAttempt.AttemptUuid,
                ["driverId"] = chosenCandidate.Driver.Id,
                ["expiresAt"] = reservationExpiry.ToString("O")
            });

            PublishEvent("DRIVER_MATCHING_SUCCEEDED", rideRequest.Uuid, correlationId, new Dictionary<string, object>
            {
                ["attemptUuid"] = successAttempt.AttemptUuid,
                ["driverId"] = chosenCandidate.Driver.Id,
                ["vehicleId"] = chosenCandidate.Vehicle.Id,
                ["distanceKm"] = chosenCandidate.DistanceKm,
                ["matchScore"] = chosenCandidate.Score
            });

            return MapToResponse(successAttempt);
        }
    }

    private async Task<RideRequest> GetRideRequestOrThrowAsync(string requestUuid)
    {
        var rideRequest = await _dbContext.RideRequests
            .Include(rr => rr.RideType)
            .FirstOrDefaultAsync(rr => rr.Uuid == requestUuid);

        if (rideRequest == null)
        {
            throw new ResourceNotFoundException($"Ride request not found with UUID: {requestUuid}");
        }

        return rideRequest;
    }

    private void PublishEvent(string eventType, string rideRequestUuid, string? correlationId, IDictionary<string, object> payload)
    {
        try
        {
            _eventPublisher.Publish(new MatchingEvent
            {
                EventType = eventType,
                RideRequestUuid = rideRequestUuid,
                CorrelationId = correlationId,
                Payload = payload
            });
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to publish event {EventType}", eventType);
        }
    }

    private static MatchingAttemptResponse MapToResponse(MatchingAttempt attempt)
    {
        DriverCandidateResponse? driverDto = null;
        if (attempt.SelectedDriver != null)
        {
            var d = attempt.SelectedDriver;
            driverDto = new DriverCandidateResponse
            {
                DriverUuid = d.User?.Uuid ?? string.Empty,
                FirstName = d.User?.FirstName ?? string.Empty,
                LastName = d.User?.LastName ?? string.Empty,
                Rating = d.Rating,
                TotalTrips = d.TotalTrips,
                AcceptanceRate = d.AcceptanceRate,
                DistanceKm = attempt.DistanceKm,
                MatchScore = attempt.MatchScore
            };
        }

        VehicleSummaryResponse? vehicleDto = null;
        if (attempt.SelectedVehicle != null)
        {
            var v = attempt.SelectedVehicle;
            vehicleDto = new VehicleSummaryResponse
            {
                VehicleUuid = v.Uuid,
                Make = v.Make,
                Model = v.Model,
                Color = v.Color,
                LicensePlate = v.LicensePlate,
                SeatCapacity = v.SeatCapacity,
                FuelType = v.FuelType.ToString(),
                RideTypeCategory = v.RideTypeCategory
            };
        }

        return new MatchingAttemptResponse
        {
            AttemptUuid = attempt.AttemptUuid,
            RideRequestUuid = attempt.RideRequestUuid,
            Status = attempt.Status,
            AttemptNumber = attempt.AttemptNumber,
            CandidatesEvaluated = attempt.CandidatesEvaluated,
            Driver = driverDto,
            Vehicle = vehicleDto,
            DistanceKm = attempt.DistanceKm,
            MatchScore = attempt.MatchScore,
            FailureReason = attempt.FailureReason,
            CreatedAt = attempt.CreatedAt,
            CompletedAt = attempt.CompletedAt
        };
    }

    private record CandidateEvaluation(
        DriverProfile Driver,
        Vehicle Vehicle,
        DriverLocation Location,
        double DistanceKm,
        decimal Score
    );
}
