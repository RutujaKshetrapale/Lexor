package com.lexor.matching;

import com.lexor.matching.config.MatchingProperties;
import com.lexor.matching.dto.request.MatchingRetryRequest;
import com.lexor.matching.dto.response.MatchingAttemptResponse;
import com.lexor.matching.entity.AssignmentStatus;
import com.lexor.matching.entity.BackgroundCheckStatus;
import com.lexor.matching.entity.DriverLocation;
import com.lexor.matching.entity.DriverProfile;
import com.lexor.matching.entity.DriverReservation;
import com.lexor.matching.entity.DriverStatus;
import com.lexor.matching.entity.DriverVehicleAssignment;
import com.lexor.matching.entity.FuelType;
import com.lexor.matching.entity.MatchingAttempt;
import com.lexor.matching.entity.MatchingStatus;
import com.lexor.matching.entity.OnboardingStatus;
import com.lexor.matching.entity.ReservationStatus;
import com.lexor.matching.entity.RideRequest;
import com.lexor.matching.entity.RideRequestStatus;
import com.lexor.matching.entity.RideType;
import com.lexor.matching.entity.Role;
import com.lexor.matching.entity.User;
import com.lexor.matching.entity.UserStatus;
import com.lexor.matching.entity.Vehicle;
import com.lexor.matching.entity.VehicleVerificationStatus;
import com.lexor.matching.event.EventPublisher;
import com.lexor.matching.event.MatchingEvent;
import com.lexor.matching.exception.ConflictException;
import com.lexor.matching.exception.ResourceNotFoundException;
import com.lexor.matching.repository.DriverProfileRepository;
import com.lexor.matching.repository.DriverReservationRepository;
import com.lexor.matching.repository.DriverVehicleAssignmentRepository;
import com.lexor.matching.repository.MatchingAttemptRepository;
import com.lexor.matching.repository.RideRequestRepository;
import com.lexor.matching.repository.VehicleRepository;
import com.lexor.matching.service.DriverLocationService;
import com.lexor.matching.service.ScoringPolicyService;
import com.lexor.matching.service.impl.DriverMatchingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverMatchingServiceTest {

    @Mock
    private RideRequestRepository rideRequestRepository;

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private DriverVehicleAssignmentRepository assignmentRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private DriverLocationService driverLocationService;

    @Mock
    private ScoringPolicyService scoringPolicyService;

    @Mock
    private MatchingAttemptRepository matchingAttemptRepository;

    @Mock
    private DriverReservationRepository driverReservationRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private MatchingProperties matchingProperties;

    @InjectMocks
    private DriverMatchingServiceImpl driverMatchingService;

    private RideRequest validRideRequest;
    private RideType goRideType;
    private DriverProfile eligibleDriver;
    private Vehicle eligibleVehicle;
    private DriverVehicleAssignment validAssignment;
    private DriverLocation freshLocation;

    @BeforeEach
    void setUp() {
        goRideType = RideType.builder()
                .id(1L)
                .uuid("a1b2c3d4-0001-4000-8000-000000000001")
                .name("LEXOR Go")
                .code("GO")
                .baseFare(new BigDecimal("50.00"))
                .perKmRate(new BigDecimal("15.00"))
                .perMinuteRate(new BigDecimal("2.50"))
                .minimumFare(new BigDecimal("80.00"))
                .build();

        validRideRequest = RideRequest.builder()
                .id(10L)
                .uuid("req-1111-2222-3333")
                .riderId(1L)
                .rideType(goRideType)
                .pickupAddress("MG Road Bengaluru")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Prestige Tech Park Bengaluru")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .estimatedDistanceKm(new BigDecimal("11.50"))
                .estimatedDurationMinutes(32)
                .estimatedFare(new BigDecimal("250.00"))
                .status(RideRequestStatus.REQUESTED)
                .build();

        User driverUser = User.builder()
                .id(3L)
                .uuid("usr-3333-4444")
                .firstName("Vikram")
                .lastName("Singh")
                .email("vikram.singh@example.com")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .build();

        eligibleDriver = DriverProfile.builder()
                .id(1L)
                .user(driverUser)
                .licenseNumber("KA-01-20230009876")
                .status(DriverStatus.ONLINE)
                .onboardingStatus(OnboardingStatus.VERIFIED)
                .isVerified(true)
                .backgroundCheckStatus(BackgroundCheckStatus.PASSED)
                .rating(new BigDecimal("4.90"))
                .totalTrips(145)
                .acceptanceRate(new BigDecimal("98.50"))
                .build();

        eligibleVehicle = Vehicle.builder()
                .id(1L)
                .uuid("veh-1111-2222")
                .driver(eligibleDriver)
                .make("Toyota")
                .model("Urban Cruiser Hyryder")
                .year(2023)
                .color("Midnight Black")
                .licensePlate("KA-01-EQ-4829")
                .rideTypeCategory("GO")
                .seatCapacity(4)
                .fuelType(FuelType.HYBRID)
                .isActive(true)
                .isInspected(true)
                .verificationStatus(VehicleVerificationStatus.VERIFIED)
                .build();

        validAssignment = DriverVehicleAssignment.builder()
                .id(1L)
                .driver(eligibleDriver)
                .vehicle(eligibleVehicle)
                .status(AssignmentStatus.ACTIVE)
                .build();

        freshLocation = DriverLocation.builder()
                .id(50L)
                .driver(eligibleDriver)
                .latitude(new BigDecimal("12.97500000"))
                .longitude(new BigDecimal("77.64500000"))
                .recordedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Successfully select and reserve eligible candidate driver")
    void testMatchSuccessWithEligibleDriver() {
        when(rideRequestRepository.findByUuid("req-1111-2222-3333")).thenReturn(Optional.of(validRideRequest));
        when(matchingAttemptRepository.findFirstByRideRequestUuidOrderByAttemptNumberDesc("req-1111-2222-3333"))
                .thenReturn(Optional.empty());
        when(matchingAttemptRepository.countByRideRequestUuid("req-1111-2222-3333")).thenReturn(0);
        when(driverProfileRepository.findEligibleOnlineDrivers(DriverStatus.ONLINE, OnboardingStatus.VERIFIED))
                .thenReturn(List.of(eligibleDriver));
        when(driverReservationRepository.findActiveReservationsForDriver(eq(1L), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(assignmentRepository.findFirstByDriverIdAndStatus(1L, AssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(validAssignment));
        when(driverLocationService.getLatestLocation(1L)).thenReturn(Optional.of(freshLocation));
        when(matchingProperties.getMaxLocationAgeSeconds()).thenReturn(300L);
        when(driverLocationService.isLocationFresh(freshLocation, 300L)).thenReturn(true);
        when(scoringPolicyService.calculateHaversineDistanceKm(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(1.50);
        when(matchingProperties.getMaxRadiusKm()).thenReturn(10.0);
        when(scoringPolicyService.calculateMatchScore(eligibleDriver, 1.50, 10.0))
                .thenReturn(new BigDecimal("91.20"));
        when(matchingProperties.getReservationTtlSeconds()).thenReturn(120L);

        when(matchingAttemptRepository.save(any(MatchingAttempt.class))).thenAnswer(invocation -> {
            MatchingAttempt a = invocation.getArgument(0);
            a.setId(101L);
            return a;
        });

        MatchingAttemptResponse response = driverMatchingService.initiateMatching("req-1111-2222-3333", "corr-1");

        assertNotNull(response);
        assertEquals(MatchingStatus.MATCHED, response.getStatus());
        assertNotNull(response.getDriver());
        assertEquals("Vikram", response.getDriver().getFirstName());
        assertEquals(new BigDecimal("1.50"), response.getDistanceKm());
        assertEquals(new BigDecimal("91.20"), response.getMatchScore());

        verify(driverReservationRepository).save(any(DriverReservation.class));
        verify(eventPublisher, org.mockito.Mockito.atLeastOnce()).publish(any(MatchingEvent.class));
    }

    @Test
    @DisplayName("Return NO_DRIVERS_FOUND when no drivers are online")
    void testNoEligibleDriversWhenAllOffline() {
        when(rideRequestRepository.findByUuid("req-1111-2222-3333")).thenReturn(Optional.of(validRideRequest));
        when(matchingAttemptRepository.findFirstByRideRequestUuidOrderByAttemptNumberDesc("req-1111-2222-3333"))
                .thenReturn(Optional.empty());
        when(matchingAttemptRepository.countByRideRequestUuid("req-1111-2222-3333")).thenReturn(0);
        when(driverProfileRepository.findEligibleOnlineDrivers(DriverStatus.ONLINE, OnboardingStatus.VERIFIED))
                .thenReturn(Collections.emptyList());
        when(matchingProperties.getMaxRadiusKm()).thenReturn(10.0);

        when(matchingAttemptRepository.save(any(MatchingAttempt.class))).thenAnswer(invocation -> {
            MatchingAttempt a = invocation.getArgument(0);
            a.setId(102L);
            return a;
        });

        MatchingAttemptResponse response = driverMatchingService.initiateMatching("req-1111-2222-3333", "corr-2");

        assertNotNull(response);
        assertEquals(MatchingStatus.NO_DRIVERS_FOUND, response.getStatus());
        assertEquals(0, response.getCandidatesEvaluated());
    }

    @Test
    @DisplayName("Exclude candidate driver when GPS location is missing")
    void testExcludeDriverWithMissingLocation() {
        when(rideRequestRepository.findByUuid("req-1111-2222-3333")).thenReturn(Optional.of(validRideRequest));
        when(matchingAttemptRepository.findFirstByRideRequestUuidOrderByAttemptNumberDesc("req-1111-2222-3333"))
                .thenReturn(Optional.empty());
        when(matchingAttemptRepository.countByRideRequestUuid("req-1111-2222-3333")).thenReturn(0);
        when(driverProfileRepository.findEligibleOnlineDrivers(DriverStatus.ONLINE, OnboardingStatus.VERIFIED))
                .thenReturn(List.of(eligibleDriver));
        when(driverReservationRepository.findActiveReservationsForDriver(eq(1L), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(assignmentRepository.findFirstByDriverIdAndStatus(1L, AssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(validAssignment));
        when(driverLocationService.getLatestLocation(1L)).thenReturn(Optional.empty());
        when(matchingProperties.getMaxRadiusKm()).thenReturn(10.0);

        when(matchingAttemptRepository.save(any(MatchingAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchingAttemptResponse response = driverMatchingService.initiateMatching("req-1111-2222-3333", "corr-3");

        assertEquals(MatchingStatus.NO_DRIVERS_FOUND, response.getStatus());
    }

    @Test
    @DisplayName("Exclude candidate driver when GPS location is stale")
    void testExcludeDriverWithStaleLocation() {
        when(rideRequestRepository.findByUuid("req-1111-2222-3333")).thenReturn(Optional.of(validRideRequest));
        when(matchingAttemptRepository.findFirstByRideRequestUuidOrderByAttemptNumberDesc("req-1111-2222-3333"))
                .thenReturn(Optional.empty());
        when(matchingAttemptRepository.countByRideRequestUuid("req-1111-2222-3333")).thenReturn(0);
        when(driverProfileRepository.findEligibleOnlineDrivers(DriverStatus.ONLINE, OnboardingStatus.VERIFIED))
                .thenReturn(List.of(eligibleDriver));
        when(driverReservationRepository.findActiveReservationsForDriver(eq(1L), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(assignmentRepository.findFirstByDriverIdAndStatus(1L, AssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(validAssignment));
        when(driverLocationService.getLatestLocation(1L)).thenReturn(Optional.of(freshLocation));
        when(matchingProperties.getMaxLocationAgeSeconds()).thenReturn(300L);
        when(driverLocationService.isLocationFresh(freshLocation, 300L)).thenReturn(false); // Stale!
        when(matchingProperties.getMaxRadiusKm()).thenReturn(10.0);

        when(matchingAttemptRepository.save(any(MatchingAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchingAttemptResponse response = driverMatchingService.initiateMatching("req-1111-2222-3333", "corr-4");

        assertEquals(MatchingStatus.NO_DRIVERS_FOUND, response.getStatus());
    }

    @Test
    @DisplayName("Exclude candidate driver beyond max matching radius")
    void testExcludeDriverBeyondMatchingRadius() {
        when(rideRequestRepository.findByUuid("req-1111-2222-3333")).thenReturn(Optional.of(validRideRequest));
        when(matchingAttemptRepository.findFirstByRideRequestUuidOrderByAttemptNumberDesc("req-1111-2222-3333"))
                .thenReturn(Optional.empty());
        when(matchingAttemptRepository.countByRideRequestUuid("req-1111-2222-3333")).thenReturn(0);
        when(driverProfileRepository.findEligibleOnlineDrivers(DriverStatus.ONLINE, OnboardingStatus.VERIFIED))
                .thenReturn(List.of(eligibleDriver));
        when(driverReservationRepository.findActiveReservationsForDriver(eq(1L), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(assignmentRepository.findFirstByDriverIdAndStatus(1L, AssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(validAssignment));
        when(driverLocationService.getLatestLocation(1L)).thenReturn(Optional.of(freshLocation));
        when(matchingProperties.getMaxLocationAgeSeconds()).thenReturn(300L);
        when(driverLocationService.isLocationFresh(freshLocation, 300L)).thenReturn(true);
        when(scoringPolicyService.calculateHaversineDistanceKm(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(15.50); // 15.5 km away
        when(matchingProperties.getMaxRadiusKm()).thenReturn(10.0); // max 10 km

        when(matchingAttemptRepository.save(any(MatchingAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchingAttemptResponse response = driverMatchingService.initiateMatching("req-1111-2222-3333", "corr-5");

        assertEquals(MatchingStatus.NO_DRIVERS_FOUND, response.getStatus());
    }

    @Test
    @DisplayName("Exclude driver when vehicle category is incompatible with requested ride type")
    void testExcludeDriverWithIncompatibleVehicle() {
        eligibleVehicle.setRideTypeCategory("PREMIER"); // Request asks for GO

        when(rideRequestRepository.findByUuid("req-1111-2222-3333")).thenReturn(Optional.of(validRideRequest));
        when(matchingAttemptRepository.findFirstByRideRequestUuidOrderByAttemptNumberDesc("req-1111-2222-3333"))
                .thenReturn(Optional.empty());
        when(matchingAttemptRepository.countByRideRequestUuid("req-1111-2222-3333")).thenReturn(0);
        when(driverProfileRepository.findEligibleOnlineDrivers(DriverStatus.ONLINE, OnboardingStatus.VERIFIED))
                .thenReturn(List.of(eligibleDriver));
        when(driverReservationRepository.findActiveReservationsForDriver(eq(1L), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(assignmentRepository.findFirstByDriverIdAndStatus(1L, AssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(validAssignment));
        when(matchingProperties.getMaxRadiusKm()).thenReturn(10.0);

        when(matchingAttemptRepository.save(any(MatchingAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchingAttemptResponse response = driverMatchingService.initiateMatching("req-1111-2222-3333", "corr-6");

        assertEquals(MatchingStatus.NO_DRIVERS_FOUND, response.getStatus());
    }

    @Test
    @DisplayName("Exclude driver with existing active reservation lock")
    void testExcludeDriverWithActiveReservationLock() {
        DriverReservation activeLock = DriverReservation.builder()
                .id(999L)
                .status(ReservationStatus.RESERVED)
                .expiresAt(LocalDateTime.now().plusSeconds(100))
                .build();

        when(rideRequestRepository.findByUuid("req-1111-2222-3333")).thenReturn(Optional.of(validRideRequest));
        when(matchingAttemptRepository.findFirstByRideRequestUuidOrderByAttemptNumberDesc("req-1111-2222-3333"))
                .thenReturn(Optional.empty());
        when(matchingAttemptRepository.countByRideRequestUuid("req-1111-2222-3333")).thenReturn(0);
        when(driverProfileRepository.findEligibleOnlineDrivers(DriverStatus.ONLINE, OnboardingStatus.VERIFIED))
                .thenReturn(List.of(eligibleDriver));
        when(driverReservationRepository.findActiveReservationsForDriver(eq(1L), any(LocalDateTime.class)))
                .thenReturn(List.of(activeLock)); // Active reservation exists!
        when(matchingProperties.getMaxRadiusKm()).thenReturn(10.0);

        when(matchingAttemptRepository.save(any(MatchingAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchingAttemptResponse response = driverMatchingService.initiateMatching("req-1111-2222-3333", "corr-7");

        assertEquals(MatchingStatus.NO_DRIVERS_FOUND, response.getStatus());
    }

    @Test
    @DisplayName("Reject matching when ride request is cancelled")
    void testRejectWhenRideRequestCancelled() {
        validRideRequest.setStatus(RideRequestStatus.CANCELLED);
        when(rideRequestRepository.findByUuid("req-1111-2222-3333")).thenReturn(Optional.of(validRideRequest));

        assertThrows(ConflictException.class, () ->
                driverMatchingService.initiateMatching("req-1111-2222-3333", "corr-8"));
    }

    @Test
    @DisplayName("Throw ResourceNotFoundException when ride request does not exist")
    void testRideRequestNotFound() {
        when(rideRequestRepository.findByUuid("non-existent-uuid")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                driverMatchingService.initiateMatching("non-existent-uuid", "corr-9"));
    }

    @Test
    @DisplayName("Idempotent return when ride request is already matched")
    void testIdempotentMatch() {
        MatchingAttempt existingMatch = MatchingAttempt.builder()
                .attemptUuid("existing-attempt-uuid")
                .rideRequest(validRideRequest)
                .rideRequestUuid(validRideRequest.getUuid())
                .status(MatchingStatus.MATCHED)
                .attemptNumber(1)
                .selectedDriver(eligibleDriver)
                .selectedVehicle(eligibleVehicle)
                .distanceKm(new BigDecimal("1.20"))
                .matchScore(new BigDecimal("94.50"))
                .build();

        when(rideRequestRepository.findByUuid("req-1111-2222-3333")).thenReturn(Optional.of(validRideRequest));
        when(matchingAttemptRepository.findFirstByRideRequestUuidOrderByAttemptNumberDesc("req-1111-2222-3333"))
                .thenReturn(Optional.of(existingMatch));

        MatchingAttemptResponse response = driverMatchingService.initiateMatching("req-1111-2222-3333", "corr-10");

        assertNotNull(response);
        assertEquals("existing-attempt-uuid", response.getAttemptUuid());
        assertEquals(MatchingStatus.MATCHED, response.getStatus());
    }

    @Test
    @DisplayName("Retry matching increments attempt number")
    void testRetryMatchingIncrementsAttemptNumber() {
        MatchingAttempt prevFailed = MatchingAttempt.builder()
                .attemptUuid("prev-failed-uuid")
                .rideRequest(validRideRequest)
                .rideRequestUuid(validRideRequest.getUuid())
                .status(MatchingStatus.NO_DRIVERS_FOUND)
                .attemptNumber(1)
                .build();

        when(rideRequestRepository.findByUuid("req-1111-2222-3333")).thenReturn(Optional.of(validRideRequest));
        when(matchingAttemptRepository.findFirstByRideRequestUuidOrderByAttemptNumberDesc("req-1111-2222-3333"))
                .thenReturn(Optional.of(prevFailed));
        when(matchingAttemptRepository.countByRideRequestUuid("req-1111-2222-3333")).thenReturn(1);
        when(driverProfileRepository.findEligibleOnlineDrivers(DriverStatus.ONLINE, OnboardingStatus.VERIFIED))
                .thenReturn(Collections.emptyList());
        when(matchingProperties.getMaxRadiusKm()).thenReturn(10.0);

        when(matchingAttemptRepository.save(any(MatchingAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchingRetryRequest retryRequest = MatchingRetryRequest.builder().reason("Retry after timeout").build();
        MatchingAttemptResponse response = driverMatchingService.retryMatching("req-1111-2222-3333", retryRequest);

        assertNotNull(response);
        assertEquals(2, response.getAttemptNumber());
    }
}
