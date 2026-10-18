package com.lexor.booking.service.impl;

import com.lexor.booking.client.DriverMatchingClient;
import com.lexor.booking.client.RideRequestClient;
import com.lexor.booking.client.dto.DriverCandidateClientDto;
import com.lexor.booking.client.dto.MatchingAttemptClientDto;
import com.lexor.booking.client.dto.RideRequestClientDto;
import com.lexor.booking.client.dto.VehicleClientDto;
import com.lexor.booking.config.BookingProperties;
import com.lexor.booking.dto.request.CancelBookingRequest;
import com.lexor.booking.dto.request.CompleteTripRequest;
import com.lexor.booking.dto.request.CreateBookingRequest;
import com.lexor.booking.dto.request.DeclineBookingRequest;
import com.lexor.booking.dto.request.StartTripRequest;
import com.lexor.booking.dto.response.BookingResponse;
import com.lexor.booking.dto.response.DriverSummaryResponse;
import com.lexor.booking.dto.response.PageResponse;
import com.lexor.booking.dto.response.RideTypeSummaryResponse;
import com.lexor.booking.dto.response.RiderSummaryResponse;
import com.lexor.booking.dto.response.VehicleSummaryResponse;
import com.lexor.booking.entity.Booking;
import com.lexor.booking.entity.BookingStatus;
import com.lexor.booking.entity.DriverProfile;
import com.lexor.booking.entity.DriverReservation;
import com.lexor.booking.entity.MatchingAttempt;
import com.lexor.booking.entity.ReservationStatus;
import com.lexor.booking.entity.Ride;
import com.lexor.booking.entity.RideRequest;
import com.lexor.booking.entity.RideStatus;
import com.lexor.booking.entity.RideType;
import com.lexor.booking.entity.RiderProfile;
import com.lexor.booking.entity.Vehicle;
import com.lexor.booking.event.BookingEvent;
import com.lexor.booking.event.IEventPublisher;
import com.lexor.booking.exception.BadRequestException;
import com.lexor.booking.exception.ConflictException;
import com.lexor.booking.exception.ForbiddenException;
import com.lexor.booking.exception.ResourceNotFoundException;
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
import com.lexor.booking.service.BookingService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private static final Logger logger = LoggerFactory.getLogger(BookingServiceImpl.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final BookingRepository bookingRepository;
    private final RideRepository rideRepository;
    private final RideRequestRepository rideRequestRepository;
    private final RiderProfileRepository riderProfileRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final VehicleRepository vehicleRepository;
    private final RideTypeRepository rideTypeRepository;
    private final MatchingAttemptRepository matchingAttemptRepository;
    private final DriverReservationRepository driverReservationRepository;

    private final RideRequestClient rideRequestClient;
    private final DriverMatchingClient driverMatchingClient;
    private final PasswordEncoder passwordEncoder;
    private final IEventPublisher eventPublisher;
    private final BookingProperties bookingProperties;

    // =========================================================================
    // RIDER OPERATIONS
    // =========================================================================

    @Override
    @Transactional
    public BookingResponse createBooking(UserPrincipal riderPrincipal,
                                         CreateBookingRequest request,
                                         String bearerToken,
                                         String correlationId) {
        if (riderPrincipal == null) {
            throw new ForbiddenException("Authentication is required to create a booking");
        }

        RiderProfile rider = riderProfileRepository.findByUserUuid(riderPrincipal.getUuid())
                .orElseThrow(() -> new ResourceNotFoundException("Rider profile not found for user: " + riderPrincipal.getUuid()));

        // 1. Idempotency Check: if client supplied an idempotency key, prevent duplicate creation
        if (StringUtils.hasText(request.getIdempotencyKey())) {
            Optional<Booking> existingKeyBooking = bookingRepository.findByIdempotencyKey(request.getIdempotencyKey().trim());
            if (existingKeyBooking.isPresent()) {
                Booking existing = existingKeyBooking.get();
                if (!existing.getRider().getId().equals(rider.getId())) {
                    throw new ForbiddenException("Idempotency key was used by another rider");
                }
                logger.info("Returning existing booking {} for idempotency key {}", existing.getUuid(), request.getIdempotencyKey());
                return mapToResponse(existing, null);
            }
        }

        String requestUuid = request.getRideRequestUuid().trim();

        // 2. Validate and fetch Ride Request
        RideRequest rideRequest = rideRequestRepository.findByUuid(requestUuid)
                .orElseGet(() -> {
                    // Try downstream ride-request-service via HTTP client if not yet replicated in local view
                    RideRequestClientDto clientDto = rideRequestClient.getRideRequestByUuid(requestUuid, bearerToken)
                            .orElseThrow(() -> new ResourceNotFoundException("Ride request not found with UUID: " + requestUuid));
                    RideType rideType = rideTypeRepository.findByCode(clientDto.getRideTypeCode())
                            .orElseGet(() -> rideTypeRepository.findAll().stream().findFirst()
                                    .orElseThrow(() -> new ResourceNotFoundException("No ride type found for code: " + clientDto.getRideTypeCode())));

                    RideRequest synthesized = RideRequest.builder()
                            .uuid(clientDto.getRequestUuid())
                            .rider(rider)
                            .rideType(rideType)
                            .pickupAddress(clientDto.getPickupAddress())
                            .pickupLatitude(clientDto.getPickupLatitude())
                            .pickupLongitude(clientDto.getPickupLongitude())
                            .dropAddress(clientDto.getDropAddress())
                            .dropLatitude(clientDto.getDropLatitude())
                            .dropLongitude(clientDto.getDropLongitude())
                            .estimatedDistanceKm(clientDto.getEstimatedDistanceKm())
                            .estimatedDurationMinutes(clientDto.getEstimatedDurationMinutes())
                            .estimatedFare(clientDto.getEstimatedFare())
                            .requestedAt(clientDto.getRequestedAt() != null ? clientDto.getRequestedAt() : LocalDateTime.now())
                            .build();
                    return rideRequestRepository.save(synthesized);
                });

        // Verify ride request ownership: caller must own this ride request
        if (!rideRequest.getRider().getId().equals(rider.getId())) {
            throw new ForbiddenException("Access denied: You do not own ride request " + requestUuid);
        }

        // Check if request is in eligible state
        if (rideRequest.getStatus() != null && rideRequest.getStatus().isTerminal()) {
            throw new ConflictException("Ride request is in state " + rideRequest.getStatus() + " and cannot be booked.");
        }

        // 3. Unique check: A ride request can only have ONE booking
        Optional<Booking> existingRequestBooking = bookingRepository.findByRideRequestId(rideRequest.getId());
        if (existingRequestBooking.isPresent()) {
            Booking existing = existingRequestBooking.get();
            logger.info("Returning existing booking {} for ride request {}", existing.getUuid(), requestUuid);
            return mapToResponse(existing, null);
        }

        // 4. Resolve Driver and Vehicle from authoritative Matching Service
        DriverProfile matchedDriver = null;
        Vehicle matchedVehicle = null;

        // Check for existing active driver reservation in database
        Optional<DriverReservation> activeReservation = driverReservationRepository
                .findActiveReservationForRideRequest(rideRequest.getId(), LocalDateTime.now());

        if (activeReservation.isPresent()) {
            matchedDriver = activeReservation.get().getDriver();
            matchedVehicle = vehicleRepository.findFirstByDriverIdAndIsActiveTrue(matchedDriver.getId())
                    .orElse(null);
        }

        // If not found in local reservations, check matching attempts
        if (matchedDriver == null) {
            Optional<MatchingAttempt> attemptOpt = matchingAttemptRepository
                    .findFirstByRideRequestUuidOrderByAttemptNumberDesc(requestUuid);

            if (attemptOpt.isPresent() && attemptOpt.get().getSelectedDriver() != null) {
                matchedDriver = attemptOpt.get().getSelectedDriver();
                matchedVehicle = attemptOpt.get().getSelectedVehicle();
            }
        }

        // If still not matched, trigger or query driver-matching-service via HTTP client
        if (matchedDriver == null) {
            try {
                Optional<MatchingAttemptClientDto> matchingDtoOpt = driverMatchingClient.getMatchingStatus(requestUuid);
                if (matchingDtoOpt.isEmpty() || !"MATCHED".equalsIgnoreCase(matchingDtoOpt.get().getStatus())) {
                    matchingDtoOpt = driverMatchingClient.initiateMatching(requestUuid, correlationId);
                }

                if (matchingDtoOpt.isPresent() && "MATCHED".equalsIgnoreCase(matchingDtoOpt.get().getStatus())) {
                    MatchingAttemptClientDto dto = matchingDtoOpt.get();
                    if (dto.getDriver() != null) {
                        DriverCandidateClientDto dCandidate = dto.getDriver();
                        matchedDriver = driverProfileRepository.findByUserUuid(dCandidate.getDriverUuid())
                                .orElse(null);
                    }
                    if (dto.getVehicle() != null) {
                        VehicleClientDto vDto = dto.getVehicle();
                        matchedVehicle = vehicleRepository.findByUuid(vDto.getVehicleUuid())
                                .orElse(null);
                    }
                }
            } catch (Exception ex) {
                logger.warn("Downstream driver matching call exception: {}", ex.getMessage());
            }
        }

        if (matchedDriver == null) {
            throw new ConflictException("No eligible driver has been matched or reserved for this ride request yet.");
        }

        if (matchedVehicle == null) {
            matchedVehicle = vehicleRepository.findFirstByDriverIdAndIsActiveTrue(matchedDriver.getId())
                    .orElseThrow(() -> new ConflictException("No active approved vehicle found for matched driver"));
        }

        // 5. Concurrency check: Ensure driver has no conflicting active journeys
        boolean driverEngaged = bookingRepository.hasActiveBookingForDriver(
                matchedDriver.getId(),
                List.of(BookingStatus.IN_PROGRESS, BookingStatus.ARRIVED)
        );
        if (driverEngaged) {
            throw new ConflictException("Matched driver is currently engaged in another active trip. Please retry.");
        }

        // 6. Generate secure 4-digit OTP
        String plainOtp = String.format("%04d", SECURE_RANDOM.nextInt(10000));
        String hashedOtp = passwordEncoder.encode(plainOtp);
        LocalDateTime otpExpiry = LocalDateTime.now().plusMinutes(bookingProperties.getOtpExpirationMinutes());

        // 7. Create persistent Booking record
        Booking booking = Booking.builder()
                .uuid(UUID.randomUUID().toString())
                .rideRequest(rideRequest)
                .rider(rider)
                .driver(matchedDriver)
                .vehicle(matchedVehicle)
                .rideType(rideRequest.getRideType())
                .bookingStatus(BookingStatus.CONFIRMED)
                .otpHash(hashedOtp)
                .otpExpiresAt(otpExpiry)
                .scheduledTime(request.getScheduledTime())
                .idempotencyKey(StringUtils.hasText(request.getIdempotencyKey()) ? request.getIdempotencyKey().trim() : null)
                .pickupAddress(rideRequest.getPickupAddress())
                .pickupLatitude(rideRequest.getPickupLatitude())
                .pickupLongitude(rideRequest.getPickupLongitude())
                .dropAddress(rideRequest.getDropAddress())
                .dropLatitude(rideRequest.getDropLatitude())
                .dropLongitude(rideRequest.getDropLongitude())
                .estimatedDistanceKm(rideRequest.getEstimatedDistanceKm())
                .estimatedDurationMinutes(rideRequest.getEstimatedDurationMinutes())
                .totalFare(rideRequest.getEstimatedFare())
                .currency("INR")
                .build();

        Booking savedBooking = bookingRepository.save(booking);

        // 8. Create linked Ride entity in rides table to maintain schema consistency
        Ride ride = Ride.builder()
                .uuid(UUID.randomUUID().toString())
                .booking(savedBooking)
                .rider(rider)
                .driver(matchedDriver)
                .vehicle(matchedVehicle)
                .rideType(rideRequest.getRideType())
                .pickupAddress(rideRequest.getPickupAddress())
                .pickupLatitude(rideRequest.getPickupLatitude())
                .pickupLongitude(rideRequest.getPickupLongitude())
                .dropAddress(rideRequest.getDropAddress())
                .dropLatitude(rideRequest.getDropLatitude())
                .dropLongitude(rideRequest.getDropLongitude())
                .status(RideStatus.DRIVER_ASSIGNED)
                .requestedAt(savedBooking.getCreatedAt() != null ? savedBooking.getCreatedAt() : LocalDateTime.now())
                .build();
        rideRepository.save(ride);

        // 9. Publish event
        publishEvent("BOOKING_CREATED", savedBooking.getUuid(), requestUuid, correlationId, Map.of(
                "riderId", rider.getId(),
                "driverId", matchedDriver.getId(),
                "vehicleId", matchedVehicle.getId(),
                "status", savedBooking.getBookingStatus().name()
        ));

        // Return booking with plain OTP so the rider can communicate it to the driver
        return mapToResponse(savedBooking, plainOtp);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getMyBookings(UserPrincipal riderPrincipal,
                                                       BookingStatus status,
                                                       Pageable pageable) {
        RiderProfile rider = getRiderOrThrow(riderPrincipal);

        Page<Booking> page = (status != null)
                ? bookingRepository.findByRiderIdAndBookingStatus(rider.getId(), status, pageable)
                : bookingRepository.findByRiderId(rider.getId(), pageable);

        Page<BookingResponse> responsePage = page.map(b -> mapToResponse(b, null));
        return PageResponse.of(responsePage);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getMyBookingByUuid(UserPrincipal riderPrincipal, String bookingUuid) {
        RiderProfile rider = getRiderOrThrow(riderPrincipal);
        Booking booking = getBookingOrThrow(bookingUuid);

        if (!booking.getRider().getId().equals(rider.getId())) {
            throw new ForbiddenException("Access denied: You do not own this booking");
        }

        return mapToResponse(booking, null);
    }

    @Override
    @Transactional
    public BookingResponse cancelBookingByRider(UserPrincipal riderPrincipal,
                                                String bookingUuid,
                                                CancelBookingRequest request) {
        RiderProfile rider = getRiderOrThrow(riderPrincipal);
        Booking booking = getBookingOrThrow(bookingUuid);

        if (!booking.getRider().getId().equals(rider.getId())) {
            throw new ForbiddenException("Access denied: You do not own this booking");
        }

        // Idempotency: if already cancelled by rider, return without error
        if (booking.getBookingStatus() == BookingStatus.CANCELLED_BY_RIDER) {
            return mapToResponse(booking, null);
        }

        if (booking.getBookingStatus() == BookingStatus.COMPLETED) {
            throw new ConflictException("Cannot cancel a completed trip");
        }

        if (booking.getBookingStatus() == BookingStatus.IN_PROGRESS) {
            throw new ConflictException("Cannot cancel a trip that is already in progress");
        }

        if (!booking.getBookingStatus().canRiderCancel()) {
            throw new ConflictException("Booking cannot be cancelled in state: " + booking.getBookingStatus());
        }

        // Transition booking to CANCELLED_BY_RIDER
        booking.setBookingStatus(BookingStatus.CANCELLED_BY_RIDER);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setCancelledBy("RIDER");
        booking.setCancellationReason(request != null && StringUtils.hasText(request.getReason()) ? request.getReason().trim() : "Cancelled by rider");

        Booking savedBooking = bookingRepository.save(booking);

        // Update corresponding ride if exists
        rideRepository.findByBookingId(savedBooking.getId()).ifPresent(r -> {
            r.setStatus(RideStatus.CANCELLED);
            r.setCancelledAt(LocalDateTime.now());
            rideRepository.save(r);
        });

        // Release driver reservation if one exists
        releaseReservationForRideRequest(savedBooking.getRideRequest().getId());

        publishEvent("BOOKING_CANCELLED", savedBooking.getUuid(), savedBooking.getRideRequest().getUuid(), null, Map.of(
                "cancelledBy", "RIDER",
                "reason", savedBooking.getCancellationReason() != null ? savedBooking.getCancellationReason() : ""
        ));

        return mapToResponse(savedBooking, null);
    }

    // =========================================================================
    // DRIVER OPERATIONS
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getDriverBookings(UserPrincipal driverPrincipal,
                                                           BookingStatus status,
                                                           Pageable pageable) {
        DriverProfile driver = getDriverOrThrow(driverPrincipal);

        Page<Booking> page = (status != null)
                ? bookingRepository.findByDriverIdAndBookingStatus(driver.getId(), status, pageable)
                : bookingRepository.findByDriverId(driver.getId(), pageable);

        Page<BookingResponse> responsePage = page.map(b -> mapToResponse(b, null));
        return PageResponse.of(responsePage);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getDriverBookingByUuid(UserPrincipal driverPrincipal, String bookingUuid) {
        DriverProfile driver = getDriverOrThrow(driverPrincipal);
        Booking booking = getBookingOrThrow(bookingUuid);

        if (!booking.getDriver().getId().equals(driver.getId())) {
            throw new ForbiddenException("Access denied: You are not the assigned driver for this booking");
        }

        return mapToResponse(booking, null);
    }

    @Override
    @Transactional
    public BookingResponse acceptBookingByDriver(UserPrincipal driverPrincipal, String bookingUuid) {
        DriverProfile driver = getDriverOrThrow(driverPrincipal);
        Booking booking = getBookingOrThrow(bookingUuid);

        if (!booking.getDriver().getId().equals(driver.getId())) {
            throw new ForbiddenException("Access denied: You are not the assigned driver for this booking");
        }

        // Idempotency check
        if (booking.getBookingStatus() == BookingStatus.ACCEPTED) {
            return mapToResponse(booking, null);
        }

        if (!booking.getBookingStatus().canDriverAccept()) {
            throw new ConflictException("Cannot accept booking in current status: " + booking.getBookingStatus());
        }

        booking.setBookingStatus(BookingStatus.ACCEPTED);
        booking.setAcceptedAt(LocalDateTime.now());
        Booking savedBooking = bookingRepository.save(booking);

        // Confirm reservation lock
        driverReservationRepository.findActiveReservationForRideRequest(savedBooking.getRideRequest().getId(), LocalDateTime.now())
                .ifPresent(res -> {
                    res.setStatus(ReservationStatus.CONFIRMED);
                    driverReservationRepository.save(res);
                });

        publishEvent("BOOKING_ACCEPTED", savedBooking.getUuid(), savedBooking.getRideRequest().getUuid(), null, Map.of(
                "driverId", driver.getId()
        ));

        return mapToResponse(savedBooking, null);
    }

    @Override
    @Transactional
    public BookingResponse declineBookingByDriver(UserPrincipal driverPrincipal,
                                                 String bookingUuid,
                                                 DeclineBookingRequest request) {
        DriverProfile driver = getDriverOrThrow(driverPrincipal);
        Booking booking = getBookingOrThrow(bookingUuid);

        if (!booking.getDriver().getId().equals(driver.getId())) {
            throw new ForbiddenException("Access denied: You are not the assigned driver for this booking");
        }

        // Idempotency check
        if (booking.getBookingStatus() == BookingStatus.DECLINED_BY_DRIVER) {
            return mapToResponse(booking, null);
        }

        if (booking.getBookingStatus() == BookingStatus.IN_PROGRESS || booking.getBookingStatus() == BookingStatus.COMPLETED) {
            throw new ConflictException("Cannot decline a trip that has started or completed");
        }

        if (!booking.getBookingStatus().canDriverDecline()) {
            throw new ConflictException("Cannot decline booking in status: " + booking.getBookingStatus());
        }

        booking.setBookingStatus(BookingStatus.DECLINED_BY_DRIVER);
        booking.setCancelledBy("DRIVER");
        booking.setCancellationReason(request != null && StringUtils.hasText(request.getReason()) ? request.getReason().trim() : "Declined by driver");
        booking.setCancelledAt(LocalDateTime.now());

        Booking savedBooking = bookingRepository.save(booking);

        // Release reservation so driver is available again
        releaseReservationForRideRequest(savedBooking.getRideRequest().getId());

        publishEvent("BOOKING_DECLINED", savedBooking.getUuid(), savedBooking.getRideRequest().getUuid(), null, Map.of(
                "driverId", driver.getId(),
                "reason", savedBooking.getCancellationReason() != null ? savedBooking.getCancellationReason() : ""
        ));

        return mapToResponse(savedBooking, null);
    }

    @Override
    @Transactional
    public BookingResponse arriveBookingByDriver(UserPrincipal driverPrincipal, String bookingUuid) {
        DriverProfile driver = getDriverOrThrow(driverPrincipal);
        Booking booking = getBookingOrThrow(bookingUuid);

        if (!booking.getDriver().getId().equals(driver.getId())) {
            throw new ForbiddenException("Access denied: You are not the assigned driver for this booking");
        }

        // Idempotency check
        if (booking.getBookingStatus() == BookingStatus.ARRIVED) {
            return mapToResponse(booking, null);
        }

        if (!booking.getBookingStatus().canDriverArrive()) {
            throw new ConflictException("Cannot record arrival in status: " + booking.getBookingStatus());
        }

        booking.setBookingStatus(BookingStatus.ARRIVED);
        booking.setArrivedAt(LocalDateTime.now());
        Booking savedBooking = bookingRepository.save(booking);

        // Update rides table record
        rideRepository.findByBookingId(savedBooking.getId()).ifPresent(r -> {
            r.setStatus(RideStatus.DRIVER_ARRIVED);
            rideRepository.save(r);
        });

        publishEvent("DRIVER_ARRIVED", savedBooking.getUuid(), savedBooking.getRideRequest().getUuid(), null, Map.of(
                "driverId", driver.getId()
        ));

        return mapToResponse(savedBooking, null);
    }

    @Override
    @Transactional
    public BookingResponse startTripByDriver(UserPrincipal driverPrincipal,
                                             String bookingUuid,
                                             StartTripRequest request) {
        DriverProfile driver = getDriverOrThrow(driverPrincipal);
        Booking booking = getBookingOrThrow(bookingUuid);

        if (!booking.getDriver().getId().equals(driver.getId())) {
            throw new ForbiddenException("Access denied: You are not the assigned driver for this booking");
        }

        // Idempotency check
        if (booking.getBookingStatus() == BookingStatus.IN_PROGRESS) {
            return mapToResponse(booking, null);
        }

        if (!booking.getBookingStatus().canDriverStart()) {
            throw new ConflictException("Cannot start trip in current status: " + booking.getBookingStatus());
        }

        // OTP verification if provided
        if (request != null && StringUtils.hasText(request.getOtp())) {
            if (!passwordEncoder.matches(request.getOtp().trim(), booking.getOtpHash())) {
                throw new BadRequestException("Invalid OTP provided. Please request the 4-digit code from the rider.");
            }
            booking.setOtpVerifiedAt(LocalDateTime.now());
        }

        booking.setBookingStatus(BookingStatus.IN_PROGRESS);
        booking.setStartedAt(LocalDateTime.now());
        Booking savedBooking = bookingRepository.save(booking);

        // Update ride record
        rideRepository.findByBookingId(savedBooking.getId()).ifPresent(r -> {
            r.setStatus(RideStatus.TRIP_STARTED);
            r.setActualPickupTime(LocalDateTime.now());
            r.setStartedAt(LocalDateTime.now());
            rideRepository.save(r);
        });

        publishEvent("TRIP_STARTED", savedBooking.getUuid(), savedBooking.getRideRequest().getUuid(), null, Map.of(
                "driverId", driver.getId(),
                "otpVerified", savedBooking.getOtpVerifiedAt() != null
        ));

        return mapToResponse(savedBooking, null);
    }

    @Override
    @Transactional
    public BookingResponse completeTripByDriver(UserPrincipal driverPrincipal,
                                                String bookingUuid,
                                                CompleteTripRequest request) {
        DriverProfile driver = getDriverOrThrow(driverPrincipal);
        Booking booking = getBookingOrThrow(bookingUuid);

        if (!booking.getDriver().getId().equals(driver.getId())) {
            throw new ForbiddenException("Access denied: You are not the assigned driver for this booking");
        }

        // Idempotency: completing an already completed trip returns the existing completed state
        if (booking.getBookingStatus() == BookingStatus.COMPLETED) {
            return mapToResponse(booking, null);
        }

        if (!booking.getBookingStatus().canDriverComplete()) {
            throw new ConflictException("Cannot complete trip in status: " + booking.getBookingStatus() + ". Trip must be IN_PROGRESS.");
        }

        booking.setBookingStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(LocalDateTime.now());

        if (request != null && request.getActualDistanceKm() != null) {
            booking.setEstimatedDistanceKm(request.getActualDistanceKm());
        }
        if (request != null && request.getActualDurationMinutes() != null) {
            booking.setEstimatedDurationMinutes(request.getActualDurationMinutes());
        }

        Booking savedBooking = bookingRepository.save(booking);

        // Update rides table record
        rideRepository.findByBookingId(savedBooking.getId()).ifPresent(r -> {
            r.setStatus(RideStatus.TRIP_COMPLETED);
            r.setActualDropTime(LocalDateTime.now());
            r.setCompletedAt(LocalDateTime.now());
            if (request != null && request.getActualDistanceKm() != null) {
                r.setActualDistanceKm(request.getActualDistanceKm());
            }
            if (request != null && request.getActualDurationMinutes() != null) {
                r.setActualDurationMinutes(request.getActualDurationMinutes());
            }
            rideRepository.save(r);
        });

        // Release driver reservation
        releaseReservationForRideRequest(savedBooking.getRideRequest().getId());

        publishEvent("TRIP_COMPLETED", savedBooking.getUuid(), savedBooking.getRideRequest().getUuid(), null, Map.of(
                "driverId", driver.getId(),
                "fare", savedBooking.getTotalFare() != null ? savedBooking.getTotalFare() : 0.0
        ));

        return mapToResponse(savedBooking, null);
    }

    // =========================================================================
    // ADMIN OPERATIONS
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getAdminBookings(Long riderId,
                                                          Long driverId,
                                                          BookingStatus status,
                                                          Pageable pageable) {
        Specification<Booking> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (riderId != null) {
                predicates.add(cb.equal(root.get("rider").get("id"), riderId));
            }
            if (driverId != null) {
                predicates.add(cb.equal(root.get("driver").get("id"), driverId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("bookingStatus"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Booking> page = bookingRepository.findAll(spec, pageable);
        Page<BookingResponse> responsePage = page.map(b -> mapToResponse(b, null));
        return PageResponse.of(responsePage);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getAdminBookingByUuid(String bookingUuid) {
        Booking booking = getBookingOrThrow(bookingUuid);
        return mapToResponse(booking, null);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private RiderProfile getRiderOrThrow(UserPrincipal principal) {
        if (principal == null) {
            throw new ForbiddenException("Authentication is required");
        }
        return riderProfileRepository.findByUserUuid(principal.getUuid())
                .orElseThrow(() -> new ResourceNotFoundException("Rider profile not found for user: " + principal.getUuid()));
    }

    private DriverProfile getDriverOrThrow(UserPrincipal principal) {
        if (principal == null) {
            throw new ForbiddenException("Authentication is required");
        }
        return driverProfileRepository.findByUserUuid(principal.getUuid())
                .orElseThrow(() -> new ResourceNotFoundException("Driver profile not found for user: " + principal.getUuid()));
    }

    private Booking getBookingOrThrow(String bookingUuid) {
        return bookingRepository.findByUuid(bookingUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with UUID: " + bookingUuid));
    }

    private void releaseReservationForRideRequest(Long rideRequestId) {
        try {
            driverReservationRepository.findActiveReservationForRideRequest(rideRequestId, LocalDateTime.now())
                    .ifPresent(res -> {
                        res.setStatus(ReservationStatus.RELEASED);
                        driverReservationRepository.save(res);
                    });
        } catch (Exception ex) {
            logger.warn("Could not release driver reservation for ride request id {}: {}", rideRequestId, ex.getMessage());
        }
    }

    private void publishEvent(String eventType, String bookingUuid, String rideRequestUuid, String correlationId, Map<String, Object> payload) {
        try {
            eventPublisher.publish(BookingEvent.builder()
                    .eventType(eventType)
                    .bookingUuid(bookingUuid)
                    .rideRequestUuid(rideRequestUuid)
                    .correlationId(correlationId)
                    .payload(payload)
                    .build());
        } catch (Exception ex) {
            logger.error("Failed to publish booking event {}: {}", eventType, ex.getMessage());
        }
    }

    private BookingResponse mapToResponse(Booking booking, String plainOtp) {
        RiderSummaryResponse riderSummary = null;
        if (booking.getRider() != null && booking.getRider().getUser() != null) {
            riderSummary = RiderSummaryResponse.builder()
                    .riderUuid(booking.getRider().getUser().getUuid())
                    .firstName(booking.getRider().getUser().getFirstName())
                    .lastName(booking.getRider().getUser().getLastName())
                    .email(booking.getRider().getUser().getEmail())
                    .phoneNumber(booking.getRider().getUser().getPhoneNumber())
                    .rating(booking.getRider().getRating())
                    .build();
        }

        DriverSummaryResponse driverSummary = null;
        if (booking.getDriver() != null && booking.getDriver().getUser() != null) {
            driverSummary = DriverSummaryResponse.builder()
                    .driverUuid(booking.getDriver().getUser().getUuid())
                    .firstName(booking.getDriver().getUser().getFirstName())
                    .lastName(booking.getDriver().getUser().getLastName())
                    .phoneNumber(booking.getDriver().getUser().getPhoneNumber())
                    .rating(booking.getDriver().getRating())
                    .totalTrips(booking.getDriver().getTotalTrips())
                    .build();
        }

        VehicleSummaryResponse vehicleSummary = null;
        if (booking.getVehicle() != null) {
            vehicleSummary = VehicleSummaryResponse.builder()
                    .vehicleUuid(booking.getVehicle().getUuid())
                    .make(booking.getVehicle().getMake())
                    .model(booking.getVehicle().getModel())
                    .year(booking.getVehicle().getYear())
                    .color(booking.getVehicle().getColor())
                    .licensePlate(booking.getVehicle().getLicensePlate())
                    .build();
        }

        RideTypeSummaryResponse rideTypeSummary = null;
        if (booking.getRideType() != null) {
            rideTypeSummary = RideTypeSummaryResponse.builder()
                    .rideTypeUuid(booking.getRideType().getUuid())
                    .code(booking.getRideType().getCode())
                    .name(booking.getRideType().getName())
                    .build();
        }

        return BookingResponse.builder()
                .bookingUuid(booking.getUuid())
                .rideRequestUuid(booking.getRideRequest() != null ? booking.getRideRequest().getUuid() : null)
                .status(booking.getBookingStatus())
                .rider(riderSummary)
                .driver(driverSummary)
                .vehicle(vehicleSummary)
                .rideType(rideTypeSummary)
                .pickupAddress(booking.getPickupAddress())
                .pickupLatitude(booking.getPickupLatitude())
                .pickupLongitude(booking.getPickupLongitude())
                .dropAddress(booking.getDropAddress())
                .dropLatitude(booking.getDropLatitude())
                .dropLongitude(booking.getDropLongitude())
                .estimatedDistanceKm(booking.getEstimatedDistanceKm())
                .estimatedDurationMinutes(booking.getEstimatedDurationMinutes())
                .totalFare(booking.getTotalFare())
                .currency(booking.getCurrency() != null ? booking.getCurrency() : "INR")
                .otp(plainOtp)
                .otpVerified(booking.getOtpVerifiedAt() != null)
                .scheduledTime(booking.getScheduledTime())
                .cancellationReason(booking.getCancellationReason())
                .cancelledBy(booking.getCancelledBy())
                .acceptedAt(booking.getAcceptedAt())
                .arrivedAt(booking.getArrivedAt())
                .startedAt(booking.getStartedAt())
                .completedAt(booking.getCompletedAt())
                .cancelledAt(booking.getCancelledAt())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }
}
