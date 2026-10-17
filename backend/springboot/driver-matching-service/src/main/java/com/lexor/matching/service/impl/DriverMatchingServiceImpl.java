package com.lexor.matching.service.impl;

import com.lexor.matching.config.MatchingProperties;
import com.lexor.matching.dto.request.MatchingRetryRequest;
import com.lexor.matching.dto.response.DriverCandidateResponse;
import com.lexor.matching.dto.response.MatchingAttemptResponse;
import com.lexor.matching.dto.response.VehicleSummaryResponse;
import com.lexor.matching.entity.AssignmentStatus;
import com.lexor.matching.entity.DriverLocation;
import com.lexor.matching.entity.DriverProfile;
import com.lexor.matching.entity.DriverReservation;
import com.lexor.matching.entity.DriverStatus;
import com.lexor.matching.entity.DriverVehicleAssignment;
import com.lexor.matching.entity.MatchingAttempt;
import com.lexor.matching.entity.MatchingStatus;
import com.lexor.matching.entity.OnboardingStatus;
import com.lexor.matching.entity.ReservationStatus;
import com.lexor.matching.entity.RideRequest;
import com.lexor.matching.entity.Vehicle;
import com.lexor.matching.event.EventPublisher;
import com.lexor.matching.event.MatchingEvent;
import com.lexor.matching.exception.BadRequestException;
import com.lexor.matching.exception.ConflictException;
import com.lexor.matching.exception.ResourceNotFoundException;
import com.lexor.matching.repository.DriverLocationRepository;
import com.lexor.matching.repository.DriverProfileRepository;
import com.lexor.matching.repository.DriverReservationRepository;
import com.lexor.matching.repository.DriverVehicleAssignmentRepository;
import com.lexor.matching.repository.MatchingAttemptRepository;
import com.lexor.matching.repository.RideRequestRepository;
import com.lexor.matching.repository.VehicleRepository;
import com.lexor.matching.service.DriverLocationService;
import com.lexor.matching.service.DriverMatchingService;
import com.lexor.matching.service.ScoringPolicyService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DriverMatchingServiceImpl implements DriverMatchingService {

    private static final Logger logger = LoggerFactory.getLogger(DriverMatchingServiceImpl.class);

    private final RideRequestRepository rideRequestRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final DriverVehicleAssignmentRepository assignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverLocationService driverLocationService;
    private final ScoringPolicyService scoringPolicyService;
    private final MatchingAttemptRepository matchingAttemptRepository;
    private final DriverReservationRepository driverReservationRepository;
    private final EventPublisher eventPublisher;
    private final MatchingProperties matchingProperties;

    // Mutex for in-process atomic driver reservation
    private static final Object RESERVATION_LOCK = new Object();

    @Override
    @Transactional
    public MatchingAttemptResponse initiateMatching(String requestUuid, String correlationId) {
        RideRequest rideRequest = getRideRequestOrThrow(requestUuid);

        // Idempotency: If request is already matched, return existing match attempt
        Optional<MatchingAttempt> latestAttemptOpt = matchingAttemptRepository
                .findFirstByRideRequestUuidOrderByAttemptNumberDesc(requestUuid);
        if (latestAttemptOpt.isPresent() && latestAttemptOpt.get().getStatus() == MatchingStatus.MATCHED) {
            logger.info("Ride request [{}] is already matched. Returning idempotent attempt [{}]",
                    requestUuid, latestAttemptOpt.get().getAttemptUuid());
            return mapToResponse(latestAttemptOpt.get());
        }

        // Verify request lifecycle eligibility
        if (rideRequest.getStatus() == null || !rideRequest.getStatus().isEligibleForMatching()) {
            throw new ConflictException("Ride request with UUID " + requestUuid + " is in status " +
                    rideRequest.getStatus() + " and cannot be matched.");
        }

        int attemptNumber = matchingAttemptRepository.countByRideRequestUuid(requestUuid) + 1;
        return executeMatchingAttempt(rideRequest, attemptNumber, correlationId);
    }

    @Override
    @Transactional(readOnly = true)
    public MatchingAttemptResponse getMatchingStatus(String requestUuid) {
        MatchingAttempt attempt = matchingAttemptRepository
                .findFirstByRideRequestUuidOrderByAttemptNumberDesc(requestUuid)
                .orElseThrow(() -> new ResourceNotFoundException("No matching attempt found for ride request UUID: " + requestUuid));
        return mapToResponse(attempt);
    }

    @Override
    @Transactional
    public MatchingAttemptResponse retryMatching(String requestUuid, MatchingRetryRequest retryRequest) {
        RideRequest rideRequest = getRideRequestOrThrow(requestUuid);

        if (rideRequest.getStatus() == null || !rideRequest.getStatus().isEligibleForMatching()) {
            throw new ConflictException("Ride request with UUID " + requestUuid + " cannot be retried in status: " +
                    rideRequest.getStatus());
        }

        Optional<MatchingAttempt> latestAttemptOpt = matchingAttemptRepository
                .findFirstByRideRequestUuidOrderByAttemptNumberDesc(requestUuid);
        if (latestAttemptOpt.isPresent()) {
            MatchingAttempt latest = latestAttemptOpt.get();
            if (latest.getStatus() == MatchingStatus.MATCHED) {
                throw new ConflictException("Ride request is already matched and cannot be retried.");
            }
        }

        int attemptNumber = matchingAttemptRepository.countByRideRequestUuid(requestUuid) + 1;
        String correlationId = UUID.randomUUID().toString();
        return executeMatchingAttempt(rideRequest, attemptNumber, correlationId);
    }

    private MatchingAttemptResponse executeMatchingAttempt(RideRequest rideRequest, int attemptNumber, String correlationId) {
        LocalDateTime now = LocalDateTime.now();
        String attemptUuid = UUID.randomUUID().toString();

        publishEvent("DRIVER_MATCHING_REQUESTED", rideRequest.getUuid(), correlationId, Map.of(
                "attemptUuid", attemptUuid,
                "attemptNumber", attemptNumber,
                "pickupLat", rideRequest.getPickupLatitude(),
                "pickupLon", rideRequest.getPickupLongitude()
        ));

        // 1. Retrieve candidate drivers who are ONLINE and VERIFIED
        List<DriverProfile> onlineDrivers = driverProfileRepository.findEligibleOnlineDrivers(
                DriverStatus.ONLINE,
                OnboardingStatus.VERIFIED
        );

        String requestedCategoryCode = rideRequest.getRideType() != null ? rideRequest.getRideType().getCode() : null;

        List<CandidateEvaluation> candidateEvaluations = new ArrayList<>();

        for (DriverProfile driver : onlineDrivers) {
            // Check if driver is already occupied by a trip
            if (driver.getStatus() != DriverStatus.ONLINE) {
                continue;
            }

            // Check if driver has an active reservation
            List<DriverReservation> activeReservations = driverReservationRepository.findActiveReservationsForDriver(driver.getId(), now);
            if (!activeReservations.isEmpty()) {
                continue;
            }

            // Find driver's active vehicle assignment
            Optional<DriverVehicleAssignment> assignmentOpt = assignmentRepository
                    .findFirstByDriverIdAndStatus(driver.getId(), AssignmentStatus.ACTIVE);
            if (assignmentOpt.isEmpty()) {
                continue;
            }

            Vehicle vehicle = assignmentOpt.get().getVehicle();
            if (vehicle == null || !vehicle.isEligibleForMatching(requestedCategoryCode)) {
                continue;
            }

            // Retrieve driver's latest GPS telemetry
            Optional<DriverLocation> locationOpt = driverLocationService.getLatestLocation(driver.getId());
            if (locationOpt.isEmpty()) {
                continue; // Missing coordinates
            }

            DriverLocation loc = locationOpt.get();
            if (!driverLocationService.isLocationFresh(loc, matchingProperties.getMaxLocationAgeSeconds())) {
                continue; // Stale coordinates
            }

            // Calculate distance to pickup
            double distanceKm = scoringPolicyService.calculateHaversineDistanceKm(
                    loc.getLatitude().doubleValue(),
                    loc.getLongitude().doubleValue(),
                    rideRequest.getPickupLatitude().doubleValue(),
                    rideRequest.getPickupLongitude().doubleValue()
            );

            if (distanceKm > matchingProperties.getMaxRadiusKm()) {
                continue; // Outside matching radius
            }

            // Calculate deterministic score
            BigDecimal matchScore = scoringPolicyService.calculateMatchScore(
                    driver,
                    distanceKm,
                    matchingProperties.getMaxRadiusKm()
            );

            candidateEvaluations.add(new CandidateEvaluation(driver, vehicle, loc, distanceKm, matchScore));
        }

        // Sort candidates deterministically: Highest score first, then shortest distance, then driver ID
        candidateEvaluations.sort(Comparator
                .comparing(CandidateEvaluation::score).reversed()
                .thenComparing(CandidateEvaluation::distanceKm)
                .thenComparing(c -> c.driver().getId()));

        if (candidateEvaluations.isEmpty()) {
            MatchingAttempt failedAttempt = MatchingAttempt.builder()
                    .attemptUuid(attemptUuid)
                    .rideRequest(rideRequest)
                    .rideRequestUuid(rideRequest.getUuid())
                    .status(MatchingStatus.NO_DRIVERS_FOUND)
                    .attemptNumber(attemptNumber)
                    .candidatesEvaluated(0)
                    .failureReason("No eligible, active drivers found within " + matchingProperties.getMaxRadiusKm() + " km")
                    .completedAt(LocalDateTime.now())
                    .build();

            MatchingAttempt saved = matchingAttemptRepository.save(failedAttempt);

            publishEvent("DRIVER_MATCHING_FAILED", rideRequest.getUuid(), correlationId, Map.of(
                    "attemptUuid", attemptUuid,
                    "reason", "NO_DRIVERS_FOUND"
            ));

            return mapToResponse(saved);
        }

        // Attempt atomic reservation on candidate drivers
        CandidateEvaluation chosenCandidate = null;
        DriverReservation reservation = null;

        synchronized (RESERVATION_LOCK) {
            for (CandidateEvaluation candidate : candidateEvaluations) {
                // Re-verify driver has no active reservation under lock
                List<DriverReservation> existingLocks = driverReservationRepository
                        .findActiveReservationsForDriver(candidate.driver().getId(), LocalDateTime.now());
                if (!existingLocks.isEmpty()) {
                    continue; // Driver was claimed by concurrent request
                }

                chosenCandidate = candidate;
                break;
            }

            if (chosenCandidate == null) {
                // All candidates were claimed by concurrent requests
                MatchingAttempt failedAttempt = MatchingAttempt.builder()
                        .attemptUuid(attemptUuid)
                        .rideRequest(rideRequest)
                        .rideRequestUuid(rideRequest.getUuid())
                        .status(MatchingStatus.NO_DRIVERS_FOUND)
                        .attemptNumber(attemptNumber)
                        .candidatesEvaluated(candidateEvaluations.size())
                        .failureReason("All eligible candidates were claimed by concurrent requests")
                        .completedAt(LocalDateTime.now())
                        .build();

                MatchingAttempt saved = matchingAttemptRepository.save(failedAttempt);
                return mapToResponse(saved);
            }

            // Create matching attempt entity
            MatchingAttempt successAttempt = MatchingAttempt.builder()
                    .attemptUuid(attemptUuid)
                    .rideRequest(rideRequest)
                    .rideRequestUuid(rideRequest.getUuid())
                    .selectedDriver(chosenCandidate.driver())
                    .selectedVehicle(chosenCandidate.vehicle())
                    .status(MatchingStatus.MATCHED)
                    .attemptNumber(attemptNumber)
                    .candidatesEvaluated(candidateEvaluations.size())
                    .distanceKm(BigDecimal.valueOf(chosenCandidate.distanceKm()).setScale(2, RoundingMode.HALF_UP))
                    .matchScore(chosenCandidate.score())
                    .completedAt(LocalDateTime.now())
                    .build();

            MatchingAttempt savedAttempt = matchingAttemptRepository.save(successAttempt);

            // Create atomic reservation
            LocalDateTime reservationExpiry = LocalDateTime.now().plusSeconds(matchingProperties.getReservationTtlSeconds());
            reservation = DriverReservation.builder()
                    .reservationUuid(UUID.randomUUID().toString())
                    .driver(chosenCandidate.driver())
                    .rideRequest(rideRequest)
                    .matchingAttempt(savedAttempt)
                    .status(ReservationStatus.RESERVED)
                    .expiresAt(reservationExpiry)
                    .build();

            driverReservationRepository.save(reservation);

            publishEvent("DRIVER_ASSIGNMENT_RESERVED", rideRequest.getUuid(), correlationId, Map.of(
                    "attemptUuid", savedAttempt.getAttemptUuid(),
                    "driverId", chosenCandidate.driver().getId(),
                    "expiresAt", reservationExpiry.toString()
            ));

            publishEvent("DRIVER_MATCHING_SUCCEEDED", rideRequest.getUuid(), correlationId, Map.of(
                    "attemptUuid", savedAttempt.getAttemptUuid(),
                    "driverId", chosenCandidate.driver().getId(),
                    "vehicleId", chosenCandidate.vehicle().getId(),
                    "distanceKm", chosenCandidate.distanceKm(),
                    "matchScore", chosenCandidate.score()
            ));

            return mapToResponse(savedAttempt);
        }
    }

    private RideRequest getRideRequestOrThrow(String requestUuid) {
        return rideRequestRepository.findByUuid(requestUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Ride request not found with UUID: " + requestUuid));
    }

    private void publishEvent(String eventType, String rideRequestUuid, String correlationId, Map<String, Object> payload) {
        try {
            eventPublisher.publish(MatchingEvent.builder()
                    .eventType(eventType)
                    .rideRequestUuid(rideRequestUuid)
                    .correlationId(correlationId)
                    .payload(payload)
                    .build());
        } catch (Exception ex) {
            logger.warn("Failed to publish event {}: {}", eventType, ex.getMessage());
        }
    }

    private MatchingAttemptResponse mapToResponse(MatchingAttempt attempt) {
        DriverCandidateResponse driverDto = null;
        if (attempt.getSelectedDriver() != null) {
            DriverProfile d = attempt.getSelectedDriver();
            driverDto = DriverCandidateResponse.builder()
                    .driverUuid(d.getUser() != null ? d.getUser().getUuid() : null)
                    .firstName(d.getUser() != null ? d.getUser().getFirstName() : null)
                    .lastName(d.getUser() != null ? d.getUser().getLastName() : null)
                    .rating(d.getRating())
                    .totalTrips(d.getTotalTrips())
                    .acceptanceRate(d.getAcceptanceRate())
                    .distanceKm(attempt.getDistanceKm())
                    .matchScore(attempt.getMatchScore())
                    .build();
        }

        VehicleSummaryResponse vehicleDto = null;
        if (attempt.getSelectedVehicle() != null) {
            Vehicle v = attempt.getSelectedVehicle();
            vehicleDto = VehicleSummaryResponse.builder()
                    .vehicleUuid(v.getUuid())
                    .make(v.getMake())
                    .model(v.getModel())
                    .color(v.getColor())
                    .licensePlate(v.getLicensePlate())
                    .seatCapacity(v.getSeatCapacity())
                    .fuelType(v.getFuelType() != null ? v.getFuelType().name() : null)
                    .rideTypeCategory(v.getRideTypeCategory())
                    .build();
        }

        return MatchingAttemptResponse.builder()
                .attemptUuid(attempt.getAttemptUuid())
                .rideRequestUuid(attempt.getRideRequestUuid())
                .status(attempt.getStatus())
                .attemptNumber(attempt.getAttemptNumber())
                .candidatesEvaluated(attempt.getCandidatesEvaluated())
                .driver(driverDto)
                .vehicle(vehicleDto)
                .distanceKm(attempt.getDistanceKm())
                .matchScore(attempt.getMatchScore())
                .failureReason(attempt.getFailureReason())
                .createdAt(attempt.getCreatedAt())
                .completedAt(attempt.getCompletedAt())
                .build();
    }

    private record CandidateEvaluation(
            DriverProfile driver,
            Vehicle vehicle,
            DriverLocation location,
            double distanceKm,
            BigDecimal score
    ) {}
}
