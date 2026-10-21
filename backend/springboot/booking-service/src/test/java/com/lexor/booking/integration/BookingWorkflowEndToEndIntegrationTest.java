package com.lexor.booking.integration;

import com.lexor.booking.client.DriverMatchingClient;
import com.lexor.booking.client.RideRequestClient;
import com.lexor.booking.client.dto.DriverCandidateClientDto;
import com.lexor.booking.client.dto.MatchingAttemptClientDto;
import com.lexor.booking.client.dto.VehicleClientDto;
import com.lexor.booking.dto.request.CancelBookingRequest;
import com.lexor.booking.dto.request.CompleteTripRequest;
import com.lexor.booking.dto.request.CreateBookingRequest;
import com.lexor.booking.dto.request.DeclineBookingRequest;
import com.lexor.booking.dto.request.StartTripRequest;
import com.lexor.booking.dto.response.BookingResponse;
import com.lexor.booking.dto.response.PageResponse;
import com.lexor.booking.entity.Booking;
import com.lexor.booking.entity.BookingStatus;
import com.lexor.booking.entity.DriverProfile;
import com.lexor.booking.entity.DriverReservation;
import com.lexor.booking.entity.DriverStatus;
import com.lexor.booking.entity.MatchingAttempt;
import com.lexor.booking.entity.MatchingStatus;
import com.lexor.booking.entity.ReservationStatus;
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
import com.lexor.booking.event.IEventPublisher;
import com.lexor.booking.exception.BadRequestException;
import com.lexor.booking.exception.ConflictException;
import com.lexor.booking.exception.ForbiddenException;
import com.lexor.booking.repository.BookingRepository;
import com.lexor.booking.repository.DriverProfileRepository;
import com.lexor.booking.repository.DriverReservationRepository;
import com.lexor.booking.repository.MatchingAttemptRepository;
import com.lexor.booking.repository.RideRepository;
import com.lexor.booking.repository.RideRequestRepository;
import com.lexor.booking.repository.RideTypeRepository;
import com.lexor.booking.repository.RiderProfileRepository;
import com.lexor.booking.repository.UserRepository;
import com.lexor.booking.repository.VehicleRepository;
import com.lexor.booking.security.UserPrincipal;
import com.lexor.booking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class BookingWorkflowEndToEndIntegrationTest {

    @Autowired
    private BookingService bookingService;

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
    private MatchingAttemptRepository matchingAttemptRepository;

    @Autowired
    private DriverReservationRepository driverReservationRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RideRepository rideRepository;

    @MockBean
    private RideRequestClient rideRequestClient;

    @MockBean
    private DriverMatchingClient driverMatchingClient;

    @MockBean
    private IEventPublisher eventPublisher;

    private User riderUser;
    private RiderProfile riderProfile;
    private User driverUser;
    private DriverProfile driverProfile;
    private Vehicle vehicle;
    private RideType rideType;
    private RideRequest rideRequest;
    private MatchingAttempt matchingAttempt;
    private DriverReservation driverReservation;

    private User otherRiderUser;
    private RiderProfile otherRiderProfile;
    private User otherDriverUser;
    private DriverProfile otherDriverProfile;

    private UserPrincipal riderPrincipal;
    private UserPrincipal otherRiderPrincipal;
    private UserPrincipal driverPrincipal;
    private UserPrincipal otherDriverPrincipal;

    @BeforeEach
    void setUp() {
        rideRepository.deleteAll();
        bookingRepository.deleteAll();
        driverReservationRepository.deleteAll();
        matchingAttemptRepository.deleteAll();
        rideRequestRepository.deleteAll();
        vehicleRepository.deleteAll();
        driverProfileRepository.deleteAll();
        riderProfileRepository.deleteAll();
        rideTypeRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Primary Rider
        riderUser = userRepository.save(User.builder()
                .uuid("rider-uuid-e2e-1")
                .firstName("Rajesh")
                .lastName("Kumar")
                .email("rajesh.kumar@lexor.mobility")
                .phoneNumber("+919000000001")
                .passwordHash("hashed")
                .role(Role.RIDER)
                .status(UserStatus.ACTIVE)
                .build());

        riderProfile = riderProfileRepository.save(RiderProfile.builder()
                .user(riderUser)
                .rating(new BigDecimal("4.90"))
                .totalRides(12)
                .build());

        riderPrincipal = UserPrincipal.create(riderUser);

        // 2. Secondary Rider (for security ownership tests)
        otherRiderUser = userRepository.save(User.builder()
                .uuid("other-rider-uuid-e2e-2")
                .firstName("Suresh")
                .lastName("Rao")
                .email("suresh.rao@lexor.mobility")
                .phoneNumber("+919000000002")
                .passwordHash("hashed")
                .role(Role.RIDER)
                .status(UserStatus.ACTIVE)
                .build());

        otherRiderProfile = riderProfileRepository.save(RiderProfile.builder()
                .user(otherRiderUser)
                .rating(new BigDecimal("4.75"))
                .totalRides(5)
                .build());

        otherRiderPrincipal = UserPrincipal.create(otherRiderUser);

        // 3. Primary Driver
        driverUser = userRepository.save(User.builder()
                .uuid("driver-uuid-e2e-1")
                .firstName("Vikram")
                .lastName("Singh")
                .email("vikram.singh@lexor.mobility")
                .phoneNumber("+919000000003")
                .passwordHash("hashed")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .build());

        driverProfile = driverProfileRepository.save(DriverProfile.builder()
                .user(driverUser)
                .licenseNumber("DL-KA-01-2022-0001")
                .status(DriverStatus.ONLINE)
                .rating(new BigDecimal("4.95"))
                .totalTrips(85)
                .build());

        driverPrincipal = UserPrincipal.create(driverUser);

        // 4. Secondary Driver (for unassigned driver rejection tests)
        otherDriverUser = userRepository.save(User.builder()
                .uuid("other-driver-uuid-e2e-2")
                .firstName("Manoj")
                .lastName("Patil")
                .email("manoj.patil@lexor.mobility")
                .phoneNumber("+919000000004")
                .passwordHash("hashed")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .build());

        otherDriverProfile = driverProfileRepository.save(DriverProfile.builder()
                .user(otherDriverUser)
                .licenseNumber("DL-KA-01-2022-0002")
                .status(DriverStatus.ONLINE)
                .rating(new BigDecimal("4.60"))
                .totalTrips(20)
                .build());

        otherDriverPrincipal = UserPrincipal.create(otherDriverUser);

        // 5. Approved Active Vehicle
        vehicle = vehicleRepository.save(Vehicle.builder()
                .uuid("veh-uuid-e2e-1")
                .driver(driverProfile)
                .make("Toyota")
                .model("Hyryder")
                .year(2023)
                .color("Black")
                .licensePlate("KA-01-EQ-4829")
                .isActive(true)
                .build());

        // 6. Ride Type
        rideType = rideTypeRepository.save(RideType.builder()
                .uuid("rt-uuid-e2e-go")
                .code("GO")
                .name("LEXOR Go")
                .baseFare(new BigDecimal("50.00"))
                .perKmRate(new BigDecimal("15.00"))
                .perMinuteRate(new BigDecimal("2.00"))
                .minimumFare(new BigDecimal("80.00"))
                .cancellationFee(new BigDecimal("40.00"))
                .capacity(4)
                .isActive(true)
                .build());

        // 7. Ride Request
        rideRequest = rideRequestRepository.save(RideRequest.builder()
                .uuid("req-uuid-e2e-001")
                .rider(riderProfile)
                .rideType(rideType)
                .pickupAddress("Indiranagar 100ft Rd, Bengaluru")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Prestige Tech Park, Bengaluru")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .estimatedDistanceKm(new BigDecimal("11.50"))
                .estimatedDurationMinutes(32)
                .estimatedFare(new BigDecimal("250.00"))
                .status(RideRequestStatus.MATCHED)
                .requestedAt(LocalDateTime.now())
                .build());

        // 8. Matching Attempt
        matchingAttempt = matchingAttemptRepository.save(MatchingAttempt.builder()
                .attemptUuid("att-uuid-e2e-001")
                .rideRequest(rideRequest)
                .rideRequestUuid(rideRequest.getUuid())
                .selectedDriver(driverProfile)
                .selectedVehicle(vehicle)
                .status(MatchingStatus.MATCHED)
                .attemptNumber(1)
                .candidatesEvaluated(5)
                .distanceKm(new BigDecimal("1.20"))
                .matchScore(new BigDecimal("92.50"))
                .build());

        // 9. Driver Reservation
        driverReservation = driverReservationRepository.save(DriverReservation.builder()
                .reservationUuid("res-uuid-e2e-001")
                .driver(driverProfile)
                .rideRequest(rideRequest)
                .matchingAttempt(matchingAttempt)
                .status(ReservationStatus.RESERVED)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build());
    }

    @Test
    @DisplayName("Complete End-to-End Ride-Booking Lifecycle: Request -> Match -> Book -> Accept -> Arrive -> OTP Start -> Complete")
    void testCompleteEndToEndRideBookingJourney() {
        // STEP 1: Rider creates a booking
        CreateBookingRequest createRequest = new CreateBookingRequest();
        createRequest.setRideRequestUuid(rideRequest.getUuid());
        createRequest.setIdempotencyKey("idemp-e2e-001");

        BookingResponse createdBooking = bookingService.createBooking(riderPrincipal, createRequest, null, "corr-001");

        assertNotNull(createdBooking);
        assertNotNull(createdBooking.getBookingUuid());
        assertEquals(BookingStatus.CONFIRMED, createdBooking.getStatus());
        assertNotNull(createdBooking.getOtp());
        assertEquals(4, createdBooking.getOtp().length());
        assertEquals(rideRequest.getUuid(), createdBooking.getRideRequestUuid());
        assertEquals("Indiranagar 100ft Rd, Bengaluru", createdBooking.getPickupAddress());
        assertNotNull(createdBooking.getDriver());
        assertEquals("Vikram", createdBooking.getDriver().getFirstName());
        assertEquals("KA-01-EQ-4829", createdBooking.getVehicle().getLicensePlate());

        String bookingUuid = createdBooking.getBookingUuid();
        String validOtp = createdBooking.getOtp();

        // Verify linked Ride entity in DB
        Booking savedBooking = bookingRepository.findByUuid(bookingUuid).orElseThrow();
        Ride linkedRide = rideRepository.findByBookingId(savedBooking.getId()).orElseThrow();
        assertEquals(RideStatus.DRIVER_ASSIGNED, linkedRide.getStatus());

        // STEP 2: Idempotency Verification
        BookingResponse duplicateBooking = bookingService.createBooking(riderPrincipal, createRequest, null, "corr-002");
        assertEquals(bookingUuid, duplicateBooking.getBookingUuid(), "Idempotent re-submission must return same booking");
        assertEquals(1, bookingRepository.count(), "Database must strictly contain exactly 1 booking");

        // STEP 3: Security & Object-Level Authorization
        // Another rider cannot access this booking
        assertThrows(ForbiddenException.class, () ->
                bookingService.getMyBookingByUuid(otherRiderPrincipal, bookingUuid));

        // Another driver cannot accept this booking
        assertThrows(ForbiddenException.class, () ->
                bookingService.acceptBookingByDriver(otherDriverPrincipal, bookingUuid));

        // STEP 4: Assigned Driver queries assigned bookings
        PageResponse<BookingResponse> driverBookings = bookingService.getDriverBookings(
                driverPrincipal, null, PageRequest.of(0, 10));
        assertNotNull(driverBookings);
        assertFalse(driverBookings.getContent().isEmpty());
        assertTrue(driverBookings.getContent().stream().anyMatch(b -> b.getBookingUuid().equals(bookingUuid)));

        // STEP 5: Driver Acceptance
        BookingResponse accepted = bookingService.acceptBookingByDriver(driverPrincipal, bookingUuid);
        assertEquals(BookingStatus.ACCEPTED, accepted.getStatus());
        assertNotNull(accepted.getAcceptedAt());

        // Verify reservation confirmed in DB
        DriverReservation reservationInDb = driverReservationRepository.findById(driverReservation.getId()).orElseThrow();
        assertEquals(ReservationStatus.CONFIRMED, reservationInDb.getStatus());

        // STEP 6: Driver Arrival
        BookingResponse arrived = bookingService.arriveBookingByDriver(driverPrincipal, bookingUuid);
        assertEquals(BookingStatus.ARRIVED, arrived.getStatus());
        assertNotNull(arrived.getArrivedAt());

        linkedRide = rideRepository.findById(linkedRide.getId()).orElseThrow();
        assertEquals(RideStatus.DRIVER_ARRIVED, linkedRide.getStatus());

        // STEP 7: OTP Verification Failure (Invalid OTP)
        StartTripRequest badStartRequest = new StartTripRequest();
        badStartRequest.setOtp("0000"); // Incorrect OTP
        assertThrows(BadRequestException.class, () ->
                bookingService.startTripByDriver(driverPrincipal, bookingUuid, badStartRequest));

        // STEP 8: Driver Starts Trip with Valid OTP
        StartTripRequest validStartRequest = new StartTripRequest();
        validStartRequest.setOtp(validOtp);
        BookingResponse started = bookingService.startTripByDriver(driverPrincipal, bookingUuid, validStartRequest);

        assertEquals(BookingStatus.IN_PROGRESS, started.getStatus());
        assertNotNull(started.getStartedAt());
        assertTrue(started.getOtpVerified());

        linkedRide = rideRepository.findById(linkedRide.getId()).orElseThrow();
        assertEquals(RideStatus.TRIP_STARTED, linkedRide.getStatus());
        assertNotNull(linkedRide.getActualPickupTime());

        // STEP 9: Mid-Journey Cancellation Guard
        CancelBookingRequest cancelAttempt = new CancelBookingRequest();
        cancelAttempt.setReason("Rider wants to stop");
        assertThrows(ConflictException.class, () ->
                bookingService.cancelBookingByRider(riderPrincipal, bookingUuid, cancelAttempt),
                "Trips that are IN_PROGRESS cannot be cancelled");

        // STEP 10: Trip Completion
        CompleteTripRequest completeRequest = new CompleteTripRequest();
        completeRequest.setActualDistanceKm(new BigDecimal("11.80"));
        completeRequest.setActualDurationMinutes(34);

        BookingResponse completed = bookingService.completeTripByDriver(driverPrincipal, bookingUuid, completeRequest);

        assertEquals(BookingStatus.COMPLETED, completed.getStatus());
        assertNotNull(completed.getCompletedAt());
        assertEquals(new BigDecimal("11.80"), completed.getEstimatedDistanceKm());
        assertEquals(34, completed.getEstimatedDurationMinutes());

        linkedRide = rideRepository.findById(linkedRide.getId()).orElseThrow();
        assertEquals(RideStatus.TRIP_COMPLETED, linkedRide.getStatus());
        assertNotNull(linkedRide.getActualDropTime());
        assertEquals(new BigDecimal("11.80"), linkedRide.getActualDistanceKm());
    }

    @Test
    @DisplayName("Independent Rider Cancellation: Releasing Driver Reservation and Updating Ride Status")
    void testIndependentRiderCancellationWorkflow() {
        CreateBookingRequest createRequest = new CreateBookingRequest();
        createRequest.setRideRequestUuid(rideRequest.getUuid());
        BookingResponse booking = bookingService.createBooking(riderPrincipal, createRequest, null, null);

        CancelBookingRequest cancelRequest = new CancelBookingRequest();
        cancelRequest.setReason("Flight rescheduled");

        BookingResponse cancelled = bookingService.cancelBookingByRider(riderPrincipal, booking.getBookingUuid(), cancelRequest);

        assertEquals(BookingStatus.CANCELLED_BY_RIDER, cancelled.getStatus());
        assertEquals("Flight rescheduled", cancelled.getCancellationReason());
        assertEquals("RIDER", cancelled.getCancelledBy());

        // Verify reservation released
        DriverReservation res = driverReservationRepository.findById(driverReservation.getId()).orElseThrow();
        assertEquals(ReservationStatus.RELEASED, res.getStatus());

        // Verify ride record cancelled
        Booking cancelledBooking = bookingRepository.findByUuid(booking.getBookingUuid()).orElseThrow();
        Ride ride = rideRepository.findByBookingId(cancelledBooking.getId()).orElseThrow();
        assertEquals(RideStatus.CANCELLED, ride.getStatus());

        // Idempotent duplicate cancellation
        BookingResponse repeatedCancel = bookingService.cancelBookingByRider(riderPrincipal, booking.getBookingUuid(), cancelRequest);
        assertEquals(BookingStatus.CANCELLED_BY_RIDER, repeatedCancel.getStatus());
    }

    @Test
    @DisplayName("Independent Driver Decline: Releasing Driver Reservation so Driver Returns to Available Pool")
    void testIndependentDriverDeclineWorkflow() {
        CreateBookingRequest createRequest = new CreateBookingRequest();
        createRequest.setRideRequestUuid(rideRequest.getUuid());
        BookingResponse booking = bookingService.createBooking(riderPrincipal, createRequest, null, null);

        DeclineBookingRequest declineRequest = new DeclineBookingRequest();
        declineRequest.setReason("Vehicle mechanical issue");

        BookingResponse declined = bookingService.declineBookingByDriver(driverPrincipal, booking.getBookingUuid(), declineRequest);

        assertEquals(BookingStatus.DECLINED_BY_DRIVER, declined.getStatus());
        assertEquals("Vehicle mechanical issue", declined.getCancellationReason());

        // Verify reservation released
        DriverReservation res = driverReservationRepository.findById(driverReservation.getId()).orElseThrow();
        assertEquals(ReservationStatus.RELEASED, res.getStatus());
    }

    @Test
    @DisplayName("Concurrency Protection: Matched Driver currently on an active trip cannot be reserved for another concurrent trip")
    void testConcurrencyEngagedDriverGuard() {
        // Step 1: Create first booking and advance driver to IN_PROGRESS
        CreateBookingRequest req1 = new CreateBookingRequest();
        req1.setRideRequestUuid(rideRequest.getUuid());
        BookingResponse b1 = bookingService.createBooking(riderPrincipal, req1, null, null);
        bookingService.acceptBookingByDriver(driverPrincipal, b1.getBookingUuid());
        bookingService.arriveBookingByDriver(driverPrincipal, b1.getBookingUuid());

        StartTripRequest startReq = new StartTripRequest();
        startReq.setOtp(b1.getOtp());
        bookingService.startTripByDriver(driverPrincipal, b1.getBookingUuid(), startReq);

        // Step 2: Second ride request attempts to book the same driver
        RideRequest req2 = rideRequestRepository.save(RideRequest.builder()
                .uuid("req-uuid-e2e-002")
                .rider(riderProfile)
                .rideType(rideType)
                .pickupAddress("Koramangala 5th Block")
                .pickupLatitude(new BigDecimal("12.9352"))
                .pickupLongitude(new BigDecimal("77.6245"))
                .dropAddress("Electronic City Phase 1")
                .dropLatitude(new BigDecimal("12.8399"))
                .dropLongitude(new BigDecimal("77.6770"))
                .estimatedDistanceKm(new BigDecimal("14.00"))
                .estimatedDurationMinutes(35)
                .estimatedFare(new BigDecimal("300.00"))
                .status(RideRequestStatus.MATCHED)
                .requestedAt(LocalDateTime.now())
                .build());

        MatchingAttempt att2 = matchingAttemptRepository.save(MatchingAttempt.builder()
                .attemptUuid("att-uuid-e2e-002")
                .rideRequest(req2)
                .rideRequestUuid(req2.getUuid())
                .selectedDriver(driverProfile)
                .selectedVehicle(vehicle)
                .status(MatchingStatus.MATCHED)
                .attemptNumber(1)
                .build());

        driverReservationRepository.save(DriverReservation.builder()
                .reservationUuid("res-uuid-e2e-002")
                .driver(driverProfile)
                .rideRequest(req2)
                .matchingAttempt(att2)
                .status(ReservationStatus.RESERVED)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build());

        CreateBookingRequest req2Booking = new CreateBookingRequest();
        req2Booking.setRideRequestUuid(req2.getUuid());

        assertThrows(ConflictException.class, () ->
                bookingService.createBooking(riderPrincipal, req2Booking, null, null));
    }

    @Test
    @DisplayName("Downstream Driver Matching Fallback: Dynamic Initiation via DriverMatchingClient when Reservation is not pre-populated")
    void testMatchingFallbackTrigger() {
        // Clear local reservations so BookingService is forced to invoke driverMatchingClient
        driverReservationRepository.deleteAll();
        matchingAttemptRepository.deleteAll();

        MatchingAttemptClientDto matchingDto = new MatchingAttemptClientDto();
        matchingDto.setAttemptUuid("att-client-001");
        matchingDto.setStatus("MATCHED");
        matchingDto.setDistanceKm(new BigDecimal("2.5"));
        matchingDto.setMatchScore(new BigDecimal("91.0"));

        DriverCandidateClientDto driverDto = new DriverCandidateClientDto();
        driverDto.setDriverUuid(driverUser.getUuid());
        driverDto.setFirstName("Vikram");
        driverDto.setLastName("Singh");
        matchingDto.setDriver(driverDto);

        VehicleClientDto vehDto = new VehicleClientDto();
        vehDto.setVehicleUuid(vehicle.getUuid());
        vehDto.setMake("Toyota");
        vehDto.setModel("Hyryder");
        vehDto.setLicensePlate("KA-01-EQ-4829");
        matchingDto.setVehicle(vehDto);

        when(driverMatchingClient.getMatchingStatus(eq(rideRequest.getUuid())))
                .thenReturn(Optional.of(matchingDto));

        CreateBookingRequest request = new CreateBookingRequest();
        request.setRideRequestUuid(rideRequest.getUuid());

        BookingResponse response = bookingService.createBooking(riderPrincipal, request, null, "corr-fallback");

        assertNotNull(response);
        assertEquals(BookingStatus.CONFIRMED, response.getStatus());
        assertEquals("KA-01-EQ-4829", response.getVehicle().getLicensePlate());
    }
}
