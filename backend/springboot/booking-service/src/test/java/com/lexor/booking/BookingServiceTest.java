package com.lexor.booking;

import com.lexor.booking.client.DriverMatchingClient;
import com.lexor.booking.client.RideRequestClient;
import com.lexor.booking.config.BookingProperties;
import com.lexor.booking.dto.request.CancelBookingRequest;
import com.lexor.booking.dto.request.CompleteTripRequest;
import com.lexor.booking.dto.request.CreateBookingRequest;
import com.lexor.booking.dto.request.DeclineBookingRequest;
import com.lexor.booking.dto.request.StartTripRequest;
import com.lexor.booking.dto.response.BookingResponse;
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
import com.lexor.booking.repository.VehicleRepository;
import com.lexor.booking.security.UserPrincipal;
import com.lexor.booking.service.impl.BookingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private RideRepository rideRepository;
    @Mock
    private RideRequestRepository rideRequestRepository;
    @Mock
    private RiderProfileRepository riderProfileRepository;
    @Mock
    private DriverProfileRepository driverProfileRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private RideTypeRepository rideTypeRepository;
    @Mock
    private MatchingAttemptRepository matchingAttemptRepository;
    @Mock
    private DriverReservationRepository driverReservationRepository;
    @Mock
    private RideRequestClient rideRequestClient;
    @Mock
    private DriverMatchingClient driverMatchingClient;
    @Mock
    private com.lexor.booking.client.NotificationClient notificationClient;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private IEventPublisher eventPublisher;
    @Mock
    private BookingProperties bookingProperties;

    @InjectMocks
    private BookingServiceImpl bookingService;

    private User riderUser;
    private User driverUser;
    private RiderProfile riderProfile;
    private DriverProfile driverProfile;
    private Vehicle vehicle;
    private RideType rideType;
    private RideRequest rideRequest;
    private UserPrincipal riderPrincipal;
    private UserPrincipal driverPrincipal;
    private Booking booking;

    @BeforeEach
    void setUp() {
        riderUser = User.builder()
                .id(1L)
                .uuid("rider-uuid-1111")
                .firstName("Rajesh")
                .lastName("Kumar")
                .email("rajesh@lexor.mobility")
                .role(Role.RIDER)
                .status(UserStatus.ACTIVE)
                .build();

        driverUser = User.builder()
                .id(2L)
                .uuid("driver-uuid-2222")
                .firstName("Vikram")
                .lastName("Singh")
                .email("vikram@lexor.mobility")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .build();

        riderProfile = RiderProfile.builder()
                .id(10L)
                .user(riderUser)
                .rating(new BigDecimal("4.95"))
                .totalRides(12)
                .build();

        driverProfile = DriverProfile.builder()
                .id(20L)
                .user(driverUser)
                .status(DriverStatus.ONLINE)
                .rating(new BigDecimal("4.90"))
                .totalTrips(145)
                .build();

        vehicle = Vehicle.builder()
                .id(30L)
                .uuid("vehicle-uuid-3333")
                .driver(driverProfile)
                .make("Toyota")
                .model("Hyryder")
                .year(2023)
                .color("Midnight Black")
                .licensePlate("KA-01-EQ-4829")
                .isActive(true)
                .build();

        rideType = RideType.builder()
                .id(40L)
                .uuid("ridetype-uuid-4444")
                .name("LEXOR Go")
                .code("GO")
                .baseFare(new BigDecimal("50.00"))
                .perKmRate(new BigDecimal("15.00"))
                .build();

        rideRequest = RideRequest.builder()
                .id(50L)
                .uuid("request-uuid-5555")
                .rider(riderProfile)
                .rideType(rideType)
                .pickupAddress("MG Road, Indiranagar")
                .pickupLatitude(new BigDecimal("12.9784"))
                .pickupLongitude(new BigDecimal("77.6408"))
                .dropAddress("Prestige Tech Park, Marathahalli")
                .dropLatitude(new BigDecimal("12.9372"))
                .dropLongitude(new BigDecimal("77.6949"))
                .estimatedDistanceKm(new BigDecimal("11.50"))
                .estimatedDurationMinutes(32)
                .estimatedFare(new BigDecimal("250.00"))
                .status(RideRequestStatus.MATCHED)
                .requestedAt(LocalDateTime.now())
                .build();

        riderPrincipal = UserPrincipal.create(riderUser);
        driverPrincipal = UserPrincipal.create(driverUser);

        booking = Booking.builder()
                .id(100L)
                .uuid("booking-uuid-7777")
                .rideRequest(rideRequest)
                .rider(riderProfile)
                .driver(driverProfile)
                .vehicle(vehicle)
                .rideType(rideType)
                .bookingStatus(BookingStatus.CONFIRMED)
                .otpHash("$2a$12$hashedOtpValue")
                .pickupAddress(rideRequest.getPickupAddress())
                .pickupLatitude(rideRequest.getPickupLatitude())
                .pickupLongitude(rideRequest.getPickupLongitude())
                .dropAddress(rideRequest.getDropAddress())
                .dropLatitude(rideRequest.getDropLatitude())
                .dropLongitude(rideRequest.getDropLongitude())
                .totalFare(new BigDecimal("250.00"))
                .currency("INR")
                .build();
    }

    @Test
    @DisplayName("Create booking: Successfully reserves matched driver, generates OTP, and saves ride")
    void testCreateBooking_Success() {
        when(riderProfileRepository.findByUserUuid(riderPrincipal.getUuid())).thenReturn(Optional.of(riderProfile));
        when(rideRequestRepository.findByUuid("request-uuid-5555")).thenReturn(Optional.of(rideRequest));
        when(bookingRepository.findByRideRequestId(50L)).thenReturn(Optional.empty());

        DriverReservation reservation = DriverReservation.builder()
                .id(1L)
                .reservationUuid("res-uuid-1")
                .driver(driverProfile)
                .rideRequest(rideRequest)
                .status(ReservationStatus.RESERVED)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        when(driverReservationRepository.findActiveReservationForRideRequest(eq(50L), any(LocalDateTime.class)))
                .thenReturn(Optional.of(reservation));
        when(vehicleRepository.findFirstByDriverIdAndIsActiveTrue(20L)).thenReturn(Optional.of(vehicle));
        when(bookingRepository.hasActiveBookingForDriver(eq(20L), any())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$encodedOtp");
        when(bookingProperties.getOtpExpirationMinutes()).thenReturn(120);

        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setId(100L);
            return b;
        });

        CreateBookingRequest request = CreateBookingRequest.builder()
                .rideRequestUuid("request-uuid-5555")
                .idempotencyKey("idemp-key-1")
                .build();

        BookingResponse response = bookingService.createBooking(riderPrincipal, request, "Bearer token", "corr-1");

        assertNotNull(response);
        assertEquals(BookingStatus.CONFIRMED, response.getStatus());
        assertNotNull(response.getOtp());
        assertEquals("MG Road, Indiranagar", response.getPickupAddress());
        verify(rideRepository).save(any(Ride.class));
    }

    @Test
    @DisplayName("Create booking: Idempotency returns existing booking when same idempotency key provided")
    void testCreateBooking_IdempotencyKeyReturnsExisting() {
        when(riderProfileRepository.findByUserUuid(riderPrincipal.getUuid())).thenReturn(Optional.of(riderProfile));
        when(bookingRepository.findByIdempotencyKey("idemp-key-1")).thenReturn(Optional.of(booking));

        CreateBookingRequest request = CreateBookingRequest.builder()
                .rideRequestUuid("request-uuid-5555")
                .idempotencyKey("idemp-key-1")
                .build();

        BookingResponse response = bookingService.createBooking(riderPrincipal, request, null, null);

        assertNotNull(response);
        assertEquals(booking.getUuid(), response.getBookingUuid());
    }

    @Test
    @DisplayName("Create booking: Throws ConflictException when ride request is CANCELLED")
    void testCreateBooking_CancelledRideRequest_ThrowsConflict() {
        rideRequest.setStatus(RideRequestStatus.CANCELLED);
        when(riderProfileRepository.findByUserUuid(riderPrincipal.getUuid())).thenReturn(Optional.of(riderProfile));
        when(rideRequestRepository.findByUuid("request-uuid-5555")).thenReturn(Optional.of(rideRequest));

        CreateBookingRequest request = CreateBookingRequest.builder()
                .rideRequestUuid("request-uuid-5555")
                .build();

        assertThrows(ConflictException.class, () ->
                bookingService.createBooking(riderPrincipal, request, null, null)
        );
    }

    @Test
    @DisplayName("Create booking: Throws ForbiddenException when caller does not own the ride request")
    void testCreateBooking_DifferentRider_ThrowsForbidden() {
        User otherUser = User.builder().id(999L).uuid("other-user-uuid").build();
        RiderProfile otherRider = RiderProfile.builder().id(999L).user(otherUser).build();
        rideRequest.setRider(otherRider);

        when(riderProfileRepository.findByUserUuid(riderPrincipal.getUuid())).thenReturn(Optional.of(riderProfile));
        when(rideRequestRepository.findByUuid("request-uuid-5555")).thenReturn(Optional.of(rideRequest));

        CreateBookingRequest request = CreateBookingRequest.builder()
                .rideRequestUuid("request-uuid-5555")
                .build();

        assertThrows(ForbiddenException.class, () ->
                bookingService.createBooking(riderPrincipal, request, null, null)
        );
    }

    @Test
    @DisplayName("Cancel booking: Successfully cancels confirmed booking")
    void testCancelBooking_Rider_Success() {
        when(riderProfileRepository.findByUserUuid(riderPrincipal.getUuid())).thenReturn(Optional.of(riderProfile));
        when(bookingRepository.findByUuid(booking.getUuid())).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CancelBookingRequest cancelReq = CancelBookingRequest.builder()
                .reason("Change of plans")
                .build();

        BookingResponse response = bookingService.cancelBookingByRider(riderPrincipal, booking.getUuid(), cancelReq);

        assertNotNull(response);
        assertEquals(BookingStatus.CANCELLED_BY_RIDER, response.getStatus());
        assertEquals("Change of plans", response.getCancellationReason());
    }

    @Test
    @DisplayName("Cancel booking: Rejects cancellation if trip is already IN_PROGRESS")
    void testCancelBooking_InProgress_ThrowsConflict() {
        booking.setBookingStatus(BookingStatus.IN_PROGRESS);
        when(riderProfileRepository.findByUserUuid(riderPrincipal.getUuid())).thenReturn(Optional.of(riderProfile));
        when(bookingRepository.findByUuid(booking.getUuid())).thenReturn(Optional.of(booking));

        assertThrows(ConflictException.class, () ->
                bookingService.cancelBookingByRider(riderPrincipal, booking.getUuid(), new CancelBookingRequest("late"))
        );
    }

    @Test
    @DisplayName("Cancel booking: Rejects cancellation if trip is already COMPLETED")
    void testCancelBooking_Completed_ThrowsConflict() {
        booking.setBookingStatus(BookingStatus.COMPLETED);
        when(riderProfileRepository.findByUserUuid(riderPrincipal.getUuid())).thenReturn(Optional.of(riderProfile));
        when(bookingRepository.findByUuid(booking.getUuid())).thenReturn(Optional.of(booking));

        assertThrows(ConflictException.class, () ->
                bookingService.cancelBookingByRider(riderPrincipal, booking.getUuid(), new CancelBookingRequest("done"))
        );
    }

    @Test
    @DisplayName("Driver accept: Transitions from CONFIRMED to ACCEPTED")
    void testDriverAccept_Success() {
        when(driverProfileRepository.findByUserUuid(driverPrincipal.getUuid())).thenReturn(Optional.of(driverProfile));
        when(bookingRepository.findByUuid(booking.getUuid())).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.acceptBookingByDriver(driverPrincipal, booking.getUuid());

        assertNotNull(response);
        assertEquals(BookingStatus.ACCEPTED, response.getStatus());
    }

    @Test
    @DisplayName("Driver decline: Transitions to DECLINED_BY_DRIVER and releases reservation")
    void testDriverDecline_Success() {
        when(driverProfileRepository.findByUserUuid(driverPrincipal.getUuid())).thenReturn(Optional.of(driverProfile));
        when(bookingRepository.findByUuid(booking.getUuid())).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeclineBookingRequest declineReq = DeclineBookingRequest.builder().reason("Too far").build();
        BookingResponse response = bookingService.declineBookingByDriver(driverPrincipal, booking.getUuid(), declineReq);

        assertNotNull(response);
        assertEquals(BookingStatus.DECLINED_BY_DRIVER, response.getStatus());
        assertEquals("Too far", response.getCancellationReason());
    }

    @Test
    @DisplayName("Driver arrival: Transitions from ACCEPTED to ARRIVED")
    void testDriverArrive_Success() {
        booking.setBookingStatus(BookingStatus.ACCEPTED);
        when(driverProfileRepository.findByUserUuid(driverPrincipal.getUuid())).thenReturn(Optional.of(driverProfile));
        when(bookingRepository.findByUuid(booking.getUuid())).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.arriveBookingByDriver(driverPrincipal, booking.getUuid());

        assertNotNull(response);
        assertEquals(BookingStatus.ARRIVED, response.getStatus());
    }

    @Test
    @DisplayName("Driver start trip: Verifies correct OTP and transitions to IN_PROGRESS")
    void testDriverStartTrip_ValidOtp_Success() {
        booking.setBookingStatus(BookingStatus.ARRIVED);
        when(driverProfileRepository.findByUserUuid(driverPrincipal.getUuid())).thenReturn(Optional.of(driverProfile));
        when(bookingRepository.findByUuid(booking.getUuid())).thenReturn(Optional.of(booking));
        when(passwordEncoder.matches("4829", booking.getOtpHash())).thenReturn(true);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StartTripRequest startReq = StartTripRequest.builder().otp("4829").build();
        BookingResponse response = bookingService.startTripByDriver(driverPrincipal, booking.getUuid(), startReq);

        assertNotNull(response);
        assertEquals(BookingStatus.IN_PROGRESS, response.getStatus());
        assertTrue(response.getOtpVerified());
    }

    @Test
    @DisplayName("Driver start trip: Fails when invalid OTP is provided")
    void testDriverStartTrip_InvalidOtp_ThrowsBadRequest() {
        booking.setBookingStatus(BookingStatus.ARRIVED);
        when(driverProfileRepository.findByUserUuid(driverPrincipal.getUuid())).thenReturn(Optional.of(driverProfile));
        when(bookingRepository.findByUuid(booking.getUuid())).thenReturn(Optional.of(booking));
        when(passwordEncoder.matches("0000", booking.getOtpHash())).thenReturn(false);

        StartTripRequest startReq = StartTripRequest.builder().otp("0000").build();
        assertThrows(BadRequestException.class, () ->
                bookingService.startTripByDriver(driverPrincipal, booking.getUuid(), startReq)
        );
    }

    @Test
    @DisplayName("Driver start trip: Fails when OTP is missing or blank")
    void testDriverStartTrip_MissingOtp_ThrowsBadRequest() {
        booking.setBookingStatus(BookingStatus.ARRIVED);
        when(driverProfileRepository.findByUserUuid(driverPrincipal.getUuid())).thenReturn(Optional.of(driverProfile));
        when(bookingRepository.findByUuid(booking.getUuid())).thenReturn(Optional.of(booking));

        StartTripRequest startReq = StartTripRequest.builder().otp("").build();
        assertThrows(BadRequestException.class, () ->
                bookingService.startTripByDriver(driverPrincipal, booking.getUuid(), startReq)
        );
    }

    @Test
    @DisplayName("Driver start trip: Fails when OTP has expired")
    void testDriverStartTrip_ExpiredOtp_ThrowsBadRequest() {
        booking.setBookingStatus(BookingStatus.ARRIVED);
        booking.setOtpExpiresAt(java.time.LocalDateTime.now().minusMinutes(5));
        when(driverProfileRepository.findByUserUuid(driverPrincipal.getUuid())).thenReturn(Optional.of(driverProfile));
        when(bookingRepository.findByUuid(booking.getUuid())).thenReturn(Optional.of(booking));

        StartTripRequest startReq = StartTripRequest.builder().otp("4829").build();
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                bookingService.startTripByDriver(driverPrincipal, booking.getUuid(), startReq)
        );
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    @DisplayName("Driver start trip: Enforces lockout after 3 consecutive invalid OTP attempts")
    void testDriverStartTrip_ExcessiveAttempts_LocksTrip() {
        Booking freshBooking = Booking.builder()
                .id(999L)
                .uuid("test-lockout-booking-" + java.util.UUID.randomUUID())
                .rider(riderProfile)
                .driver(driverProfile)
                .vehicle(vehicle)
                .rideType(rideType)
                .bookingStatus(BookingStatus.ARRIVED)
                .otpHash("$2a$12$sampleHashedOtp")
                .otpExpiresAt(java.time.LocalDateTime.now().plusHours(1))
                .build();

        when(driverProfileRepository.findByUserUuid(driverPrincipal.getUuid())).thenReturn(Optional.of(driverProfile));
        when(bookingRepository.findByUuid(freshBooking.getUuid())).thenReturn(Optional.of(freshBooking));
        when(passwordEncoder.matches("1111", freshBooking.getOtpHash())).thenReturn(false);

        StartTripRequest startReq = StartTripRequest.builder().otp("1111").build();

        // Attempt 1: fails
        assertThrows(BadRequestException.class, () ->
                bookingService.startTripByDriver(driverPrincipal, freshBooking.getUuid(), startReq));

        // Attempt 2: fails
        assertThrows(BadRequestException.class, () ->
                bookingService.startTripByDriver(driverPrincipal, freshBooking.getUuid(), startReq));

        // Attempt 3: fails with lockout (ConflictException)
        ConflictException ex = assertThrows(ConflictException.class, () ->
                bookingService.startTripByDriver(driverPrincipal, freshBooking.getUuid(), startReq));
        assertTrue(ex.getMessage().contains("Maximum attempts exceeded"));

        // Attempt 4: immediately locked out
        ConflictException ex2 = assertThrows(ConflictException.class, () ->
                bookingService.startTripByDriver(driverPrincipal, freshBooking.getUuid(), startReq));
        assertTrue(ex2.getMessage().contains("locked"));
    }

    @Test
    @DisplayName("Driver complete trip: Transitions IN_PROGRESS to COMPLETED with metrics")
    void testDriverCompleteTrip_Success() {
        booking.setBookingStatus(BookingStatus.IN_PROGRESS);
        when(driverProfileRepository.findByUserUuid(driverPrincipal.getUuid())).thenReturn(Optional.of(driverProfile));
        when(bookingRepository.findByUuid(booking.getUuid())).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompleteTripRequest completeReq = CompleteTripRequest.builder()
                .actualDistanceKm(new BigDecimal("11.80"))
                .actualDurationMinutes(35)
                .build();

        BookingResponse response = bookingService.completeTripByDriver(driverPrincipal, booking.getUuid(), completeReq);

        assertNotNull(response);
        assertEquals(BookingStatus.COMPLETED, response.getStatus());
        assertEquals(new BigDecimal("11.80"), response.getEstimatedDistanceKm());
    }

    @Test
    @DisplayName("Driver complete trip: Idempotent when already COMPLETED")
    void testDriverCompleteTrip_Idempotent() {
        booking.setBookingStatus(BookingStatus.COMPLETED);
        when(driverProfileRepository.findByUserUuid(driverPrincipal.getUuid())).thenReturn(Optional.of(driverProfile));
        when(bookingRepository.findByUuid(booking.getUuid())).thenReturn(Optional.of(booking));

        BookingResponse response = bookingService.completeTripByDriver(driverPrincipal, booking.getUuid(), new CompleteTripRequest());

        assertNotNull(response);
        assertEquals(BookingStatus.COMPLETED, response.getStatus());
    }

    @Test
    @DisplayName("Driver actions: Throws ForbiddenException when non-assigned driver attempts action")
    void testDriverAction_NonAssignedDriver_ThrowsForbidden() {
        User anotherDriverUser = User.builder()
                .id(888L)
                .uuid("other-driver-uuid")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .build();
        DriverProfile anotherDriver = DriverProfile.builder().id(888L).user(anotherDriverUser).build();
        UserPrincipal anotherDriverPrincipal = UserPrincipal.create(anotherDriverUser);

        when(driverProfileRepository.findByUserUuid(anotherDriverPrincipal.getUuid())).thenReturn(Optional.of(anotherDriver));
        when(bookingRepository.findByUuid(booking.getUuid())).thenReturn(Optional.of(booking));

        assertThrows(ForbiddenException.class, () ->
                bookingService.acceptBookingByDriver(anotherDriverPrincipal, booking.getUuid())
        );
    }
}
