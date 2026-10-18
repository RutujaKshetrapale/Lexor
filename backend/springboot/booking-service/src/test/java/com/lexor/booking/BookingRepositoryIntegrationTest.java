package com.lexor.booking;

import com.lexor.booking.entity.Booking;
import com.lexor.booking.entity.BookingStatus;
import com.lexor.booking.entity.DriverProfile;
import com.lexor.booking.entity.DriverStatus;
import com.lexor.booking.entity.Ride;
import com.lexor.booking.entity.RideRequest;
import com.lexor.booking.entity.RideRequestStatus;
import com.lexor.booking.entity.RideStatus;
import com.lexor.booking.entity.RideType;
import com.lexor.booking.entity.RiderProfile;
import com.lexor.booking.entity.Role;
import com.lexor.booking.entity.User;
import com.lexor.booking.entity.UserStatus;
import com.lexor.booking.entity.Vehicle;
import com.lexor.booking.repository.BookingRepository;
import com.lexor.booking.repository.DriverProfileRepository;
import com.lexor.booking.repository.RideRepository;
import com.lexor.booking.repository.RideRequestRepository;
import com.lexor.booking.repository.RideTypeRepository;
import com.lexor.booking.repository.RiderProfileRepository;
import com.lexor.booking.repository.UserRepository;
import com.lexor.booking.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class BookingRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RiderProfileRepository riderProfileRepository;

    @Autowired
    private DriverProfileRepository driverProfileRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private RideTypeRepository rideTypeRepository;

    @Autowired
    private RideRequestRepository rideRequestRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RideRepository rideRepository;

    private RiderProfile riderProfile;
    private DriverProfile driverProfile;
    private Vehicle vehicle;
    private RideType rideType;
    private RideRequest rideRequest;

    @BeforeEach
    void setUp() {
        rideRepository.deleteAll();
        bookingRepository.deleteAll();
        rideRequestRepository.deleteAll();
        vehicleRepository.deleteAll();
        driverProfileRepository.deleteAll();
        riderProfileRepository.deleteAll();
        rideTypeRepository.deleteAll();
        userRepository.deleteAll();

        User riderUser = userRepository.save(User.builder()
                .uuid(UUID.randomUUID().toString())
                .firstName("Rider")
                .lastName("Test")
                .email("rider.test@lexor.mobility")
                .phoneNumber("+919000000001")
                .passwordHash("hashed")
                .role(Role.RIDER)
                .status(UserStatus.ACTIVE)
                .build());

        riderProfile = riderProfileRepository.save(RiderProfile.builder()
                .user(riderUser)
                .rating(new BigDecimal("5.00"))
                .totalRides(0)
                .build());

        User driverUser = userRepository.save(User.builder()
                .uuid(UUID.randomUUID().toString())
                .firstName("Driver")
                .lastName("Test")
                .email("driver.test@lexor.mobility")
                .phoneNumber("+919000000002")
                .passwordHash("hashed")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .build());

        driverProfile = driverProfileRepository.save(DriverProfile.builder()
                .user(driverUser)
                .status(DriverStatus.ONLINE)
                .rating(new BigDecimal("4.90"))
                .totalTrips(10)
                .build());

        vehicle = vehicleRepository.save(Vehicle.builder()
                .uuid(UUID.randomUUID().toString())
                .driver(driverProfile)
                .make("Hyundai")
                .model("Creta")
                .year(2023)
                .color("White")
                .licensePlate("KA-05-AA-1111")
                .isActive(true)
                .build());

        rideType = rideTypeRepository.save(RideType.builder()
                .uuid(UUID.randomUUID().toString())
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

        rideRequest = rideRequestRepository.save(RideRequest.builder()
                .uuid(UUID.randomUUID().toString())
                .rider(riderProfile)
                .rideType(rideType)
                .pickupAddress("Koramangala 4th Block")
                .pickupLatitude(new BigDecimal("12.9345"))
                .pickupLongitude(new BigDecimal("77.6265"))
                .dropAddress("Indiranagar 100ft Road")
                .dropLatitude(new BigDecimal("12.9784"))
                .dropLongitude(new BigDecimal("77.6408"))
                .estimatedDistanceKm(new BigDecimal("6.50"))
                .estimatedDurationMinutes(22)
                .estimatedFare(new BigDecimal("160.00"))
                .status(RideRequestStatus.MATCHED)
                .requestedAt(LocalDateTime.now())
                .build());
    }

    @Test
    @DisplayName("Repository: Successfully save and find booking by UUID and rideRequest")
    void testSaveAndFindBooking() {
        Booking booking = Booking.builder()
                .uuid(UUID.randomUUID().toString())
                .rideRequest(rideRequest)
                .rider(riderProfile)
                .driver(driverProfile)
                .vehicle(vehicle)
                .rideType(rideType)
                .bookingStatus(BookingStatus.CONFIRMED)
                .otpHash("hashedOtp")
                .pickupAddress(rideRequest.getPickupAddress())
                .pickupLatitude(rideRequest.getPickupLatitude())
                .pickupLongitude(rideRequest.getPickupLongitude())
                .dropAddress(rideRequest.getDropAddress())
                .dropLatitude(rideRequest.getDropLatitude())
                .dropLongitude(rideRequest.getDropLongitude())
                .totalFare(new BigDecimal("160.00"))
                .currency("INR")
                .idempotencyKey("idemp-unique-123")
                .build();

        Booking saved = bookingRepository.save(booking);
        assertNotNull(saved.getId());

        Optional<Booking> foundByUuid = bookingRepository.findByUuid(saved.getUuid());
        assertTrue(foundByUuid.isPresent());
        assertEquals("Koramangala 4th Block", foundByUuid.get().getPickupAddress());

        Optional<Booking> foundByRideRequest = bookingRepository.findByRideRequestId(rideRequest.getId());
        assertTrue(foundByRideRequest.isPresent());
        assertEquals(saved.getId(), foundByRideRequest.get().getId());

        Optional<Booking> foundByIdemp = bookingRepository.findByIdempotencyKey("idemp-unique-123");
        assertTrue(foundByIdemp.isPresent());
    }

    @Test
    @DisplayName("Repository: Concurrency check hasActiveBookingForDriver returns true when active")
    void testHasActiveBookingForDriver() {
        Booking booking = Booking.builder()
                .uuid(UUID.randomUUID().toString())
                .rideRequest(rideRequest)
                .rider(riderProfile)
                .driver(driverProfile)
                .vehicle(vehicle)
                .rideType(rideType)
                .bookingStatus(BookingStatus.IN_PROGRESS)
                .otpHash("hashedOtp")
                .build();

        bookingRepository.save(booking);

        boolean hasActive = bookingRepository.hasActiveBookingForDriver(
                driverProfile.getId(),
                List.of(BookingStatus.IN_PROGRESS, BookingStatus.ARRIVED)
        );
        assertTrue(hasActive);

        boolean hasOther = bookingRepository.hasActiveBookingForDriver(
                driverProfile.getId(),
                List.of(BookingStatus.COMPLETED)
        );
        assertFalse(hasOther);
    }

    @Test
    @DisplayName("Repository: Pagination by rider and driver")
    void testPaginationQueries() {
        Booking booking = Booking.builder()
                .uuid(UUID.randomUUID().toString())
                .rideRequest(rideRequest)
                .rider(riderProfile)
                .driver(driverProfile)
                .vehicle(vehicle)
                .rideType(rideType)
                .bookingStatus(BookingStatus.CONFIRMED)
                .otpHash("hashedOtp")
                .build();

        bookingRepository.save(booking);

        Page<Booking> riderBookings = bookingRepository.findByRiderId(riderProfile.getId(), PageRequest.of(0, 10));
        assertEquals(1, riderBookings.getTotalElements());

        Page<Booking> driverBookings = bookingRepository.findByDriverId(driverProfile.getId(), PageRequest.of(0, 10));
        assertEquals(1, driverBookings.getTotalElements());
    }
}
