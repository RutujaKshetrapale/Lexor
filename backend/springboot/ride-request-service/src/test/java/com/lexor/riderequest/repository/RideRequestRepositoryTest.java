package com.lexor.riderequest.repository;

import com.lexor.riderequest.entity.RideRequest;
import com.lexor.riderequest.entity.RideRequestStatus;
import com.lexor.riderequest.entity.RideType;
import com.lexor.riderequest.entity.RiderProfile;
import com.lexor.riderequest.entity.Role;
import com.lexor.riderequest.entity.User;
import com.lexor.riderequest.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RideRequestRepositoryTest {

    @Autowired
    private RideRequestRepository rideRequestRepository;

    @Autowired
    private RiderProfileRepository riderProfileRepository;

    @Autowired
    private RideTypeRepository rideTypeRepository;

    @Autowired
    private UserRepository userRepository;

    private User riderUser;
    private RiderProfile riderProfile;
    private RideType rideType;

    @BeforeEach
    void setUp() {
        rideRequestRepository.deleteAll();
        riderProfileRepository.deleteAll();
        rideTypeRepository.deleteAll();
        userRepository.deleteAll();

        riderUser = userRepository.save(User.builder()
                .uuid(UUID.randomUUID().toString())
                .firstName("Rajesh")
                .lastName("Kumar")
                .email("rajesh.kumar@example.com")
                .phoneNumber("+919876543211")
                .passwordHash("$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFj5/5l.K1N6r7F0WpE0yP1O2Q3R4S5T")
                .role(Role.RIDER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        riderProfile = riderProfileRepository.save(RiderProfile.builder()
                .user(riderUser)
                .rating(new BigDecimal("4.85"))
                .totalRides(10)
                .preferredPaymentMethod("UPI")
                .build());

        rideType = rideTypeRepository.save(RideType.builder()
                .name("LEXOR Go")
                .code("GO")
                .baseFare(new BigDecimal("50.00"))
                .perKmRate(new BigDecimal("15.00"))
                .perMinuteRate(new BigDecimal("2.50"))
                .minimumFare(new BigDecimal("80.00"))
                .cancellationFee(new BigDecimal("30.00"))
                .capacity(4)
                .isActive(true)
                .build());
    }

    @Test
    @DisplayName("Persist ride request successfully and retrieve by UUID")
    void testSaveAndFindByUuid() {
        String uuid = UUID.randomUUID().toString();
        RideRequest request = RideRequest.builder()
                .uuid(uuid)
                .rider(riderProfile)
                .rideType(rideType)
                .pickupAddress("MG Road, Bengaluru")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Prestige Tech Park, Bengaluru")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .estimatedDistanceKm(new BigDecimal("11.50"))
                .estimatedDurationMinutes(32)
                .estimatedFare(new BigDecimal("250.00"))
                .status(RideRequestStatus.REQUESTED)
                .requestedAt(LocalDateTime.now())
                .build();

        RideRequest saved = rideRequestRepository.save(request);

        assertNotNull(saved.getId());
        Optional<RideRequest> found = rideRequestRepository.findByUuid(uuid);
        assertTrue(found.isPresent());
        assertEquals("MG Road, Bengaluru", found.get().getPickupAddress());
        assertEquals(RideRequestStatus.REQUESTED, found.get().getStatus());
        assertEquals("GO", found.get().getRideType().getCode());
        assertEquals("Rajesh", found.get().getRider().getUser().getFirstName());
    }

    @Test
    @DisplayName("Enforce unique constraint on ride request UUID")
    void testUuidUniqueConstraint() {
        String sharedUuid = UUID.randomUUID().toString();

        RideRequest request1 = RideRequest.builder()
                .uuid(sharedUuid)
                .rider(riderProfile)
                .rideType(rideType)
                .pickupAddress("Pickup 1")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Drop 1")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .estimatedDistanceKm(new BigDecimal("5.00"))
                .estimatedDurationMinutes(15)
                .estimatedFare(new BigDecimal("120.00"))
                .status(RideRequestStatus.REQUESTED)
                .requestedAt(LocalDateTime.now())
                .build();

        rideRequestRepository.saveAndFlush(request1);

        RideRequest request2 = RideRequest.builder()
                .uuid(sharedUuid)
                .rider(riderProfile)
                .rideType(rideType)
                .pickupAddress("Pickup 2")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Drop 2")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .estimatedDistanceKm(new BigDecimal("6.00"))
                .estimatedDurationMinutes(18)
                .estimatedFare(new BigDecimal("140.00"))
                .status(RideRequestStatus.REQUESTED)
                .requestedAt(LocalDateTime.now())
                .build();

        assertThrows(DataIntegrityViolationException.class, () ->
                rideRequestRepository.saveAndFlush(request2)
        );
    }

    @Test
    @DisplayName("Query ride requests by rider ID and status with pagination")
    void testFindByRiderIdAndStatusPagination() {
        // Create 3 REQUESTED and 2 CANCELLED requests
        for (int i = 1; i <= 3; i++) {
            rideRequestRepository.save(RideRequest.builder()
                    .uuid(UUID.randomUUID().toString())
                    .rider(riderProfile)
                    .rideType(rideType)
                    .pickupAddress("Pickup " + i)
                    .pickupLatitude(new BigDecimal("12.97840000"))
                    .pickupLongitude(new BigDecimal("77.64080000"))
                    .dropAddress("Drop " + i)
                    .dropLatitude(new BigDecimal("12.93720000"))
                    .dropLongitude(new BigDecimal("77.69490000"))
                    .estimatedDistanceKm(new BigDecimal("10.00"))
                    .estimatedDurationMinutes(25)
                    .estimatedFare(new BigDecimal("200.00"))
                    .status(RideRequestStatus.REQUESTED)
                    .requestedAt(LocalDateTime.now().minusHours(i))
                    .build());
        }

        for (int i = 1; i <= 2; i++) {
            rideRequestRepository.save(RideRequest.builder()
                    .uuid(UUID.randomUUID().toString())
                    .rider(riderProfile)
                    .rideType(rideType)
                    .pickupAddress("Cancelled Pickup " + i)
                    .pickupLatitude(new BigDecimal("12.97840000"))
                    .pickupLongitude(new BigDecimal("77.64080000"))
                    .dropAddress("Cancelled Drop " + i)
                    .dropLatitude(new BigDecimal("12.93720000"))
                    .dropLongitude(new BigDecimal("77.69490000"))
                    .estimatedDistanceKm(new BigDecimal("8.00"))
                    .estimatedDurationMinutes(20)
                    .estimatedFare(new BigDecimal("160.00"))
                    .status(RideRequestStatus.CANCELLED)
                    .requestedAt(LocalDateTime.now().minusDays(i))
                    .build());
        }

        Page<RideRequest> requestedPage = rideRequestRepository.findByRiderIdAndStatus(
                riderProfile.getId(),
                RideRequestStatus.REQUESTED,
                PageRequest.of(0, 10, Sort.by("requestedAt").descending())
        );

        assertEquals(3, requestedPage.getTotalElements());

        Page<RideRequest> cancelledPage = rideRequestRepository.findByRiderIdAndStatus(
                riderProfile.getId(),
                RideRequestStatus.CANCELLED,
                PageRequest.of(0, 10, Sort.by("requestedAt").descending())
        );

        assertEquals(2, cancelledPage.getTotalElements());
    }
}
