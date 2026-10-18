using System.Security.Cryptography;
using BookingService.Clients;
using BookingService.Clients.DTOs;
using BookingService.Configuration;
using BookingService.Data;
using BookingService.DTOs.Request;
using BookingService.DTOs.Response;
using BookingService.Entities;
using BookingService.Enums;
using BookingService.Events;
using BookingService.Exceptions;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace BookingService.Services;

public class BookingService : IBookingService
{
    private readonly LexorDbContext _dbContext;
    private readonly IRideRequestClient _rideRequestClient;
    private readonly IDriverMatchingClient _driverMatchingClient;
    private readonly IEventPublisher _eventPublisher;
    private readonly BookingSettings _bookingSettings;
    private readonly ILogger<BookingService> _logger;

    public BookingService(
        LexorDbContext dbContext,
        IRideRequestClient rideRequestClient,
        IDriverMatchingClient driverMatchingClient,
        IEventPublisher eventPublisher,
        IOptions<BookingSettings> bookingSettings,
        ILogger<BookingService> logger)
    {
        _dbContext = dbContext;
        _rideRequestClient = rideRequestClient;
        _driverMatchingClient = driverMatchingClient;
        _eventPublisher = eventPublisher;
        _bookingSettings = bookingSettings.Value;
        _logger = logger;
    }

    // =========================================================================
    // RIDER OPERATIONS
    // =========================================================================

    public async Task<BookingResponse> CreateBookingAsync(
        string riderUserUuid,
        CreateBookingDto request,
        string? bearerToken = null,
        string? correlationId = null)
    {
        if (string.IsNullOrWhiteSpace(riderUserUuid))
        {
            throw new ForbiddenException("Authentication is required to create a booking");
        }

        var rider = await _dbContext.RiderProfiles
            .Include(r => r.User)
            .FirstOrDefaultAsync(r => r.User.Uuid == riderUserUuid)
            ?? throw new ResourceNotFoundException($"Rider profile not found for user: {riderUserUuid}");

        // 1. Idempotency Check
        if (!string.IsNullOrWhiteSpace(request.IdempotencyKey))
        {
            var existingKeyBooking = await GetBookingQuery()
                .FirstOrDefaultAsync(b => b.IdempotencyKey == request.IdempotencyKey.Trim());

            if (existingKeyBooking != null)
            {
                if (existingKeyBooking.RiderId != rider.Id)
                {
                    throw new ForbiddenException("Idempotency key was used by another rider");
                }
                _logger.LogInformation("Returning existing booking {Uuid} for idempotency key {Key}", existingKeyBooking.Uuid, request.IdempotencyKey);
                return MapToResponse(existingKeyBooking);
            }
        }

        var requestUuid = request.RideRequestUuid.Trim();

        // 2. Validate and fetch Ride Request
        var rideRequest = await _dbContext.RideRequests
            .Include(r => r.Rider)
            .Include(r => r.RideType)
            .FirstOrDefaultAsync(r => r.Uuid == requestUuid);

        if (rideRequest == null)
        {
            var clientDto = await _rideRequestClient.GetRideRequestByUuidAsync(requestUuid, bearerToken)
                ?? throw new ResourceNotFoundException($"Ride request not found with UUID: {requestUuid}");

            var rideType = await _dbContext.RideTypes
                .FirstOrDefaultAsync(rt => rt.Code == clientDto.RideTypeCode)
                ?? await _dbContext.RideTypes.FirstOrDefaultAsync()
                ?? throw new ResourceNotFoundException($"No ride type found for code: {clientDto.RideTypeCode}");

            rideRequest = new RideRequest
            {
                Uuid = clientDto.RequestUuid,
                RiderId = rider.Id,
                Rider = rider,
                RideTypeId = rideType.Id,
                RideType = rideType,
                PickupAddress = clientDto.PickupAddress,
                PickupLatitude = clientDto.PickupLatitude,
                PickupLongitude = clientDto.PickupLongitude,
                DropAddress = clientDto.DropAddress,
                DropLatitude = clientDto.DropLatitude,
                DropLongitude = clientDto.DropLongitude,
                EstimatedDistanceKm = clientDto.EstimatedDistanceKm ?? 0m,
                EstimatedDurationMinutes = clientDto.EstimatedDurationMinutes ?? 0,
                EstimatedFare = clientDto.EstimatedFare ?? 0m,
                RequestedAt = clientDto.RequestedAt != default ? clientDto.RequestedAt : DateTime.UtcNow,
                CreatedAt = DateTime.UtcNow,
                UpdatedAt = DateTime.UtcNow
            };

            _dbContext.RideRequests.Add(rideRequest);
            await _dbContext.SaveChangesAsync();
        }

        // Verify ride request ownership
        if (rideRequest.RiderId != rider.Id)
        {
            throw new ForbiddenException($"Access denied: You do not own ride request {requestUuid}");
        }

        // Check if request is in eligible state
        if (rideRequest.Status.IsTerminal())
        {
            throw new ConflictException($"Ride request is in state {rideRequest.Status} and cannot be booked.");
        }

        // 3. Unique check: A ride request can only have ONE booking
        var existingRequestBooking = await GetBookingQuery()
            .FirstOrDefaultAsync(b => b.RideRequestId == rideRequest.Id);

        if (existingRequestBooking != null)
        {
            _logger.LogInformation("Returning existing booking {Uuid} for ride request {RequestUuid}", existingRequestBooking.Uuid, requestUuid);
            return MapToResponse(existingRequestBooking);
        }

        // 4. Resolve Driver and Vehicle from authoritative Matching Service
        DriverProfile? matchedDriver = null;
        Vehicle? matchedVehicle = null;

        // Check for active local driver reservation
        var activeReservation = await _dbContext.DriverReservations
            .Include(r => r.Driver).ThenInclude(d => d.User)
            .FirstOrDefaultAsync(r => r.RideRequestId == rideRequest.Id &&
                                      r.Status == ReservationStatus.RESERVED &&
                                      r.ExpiresAt > DateTime.UtcNow);

        if (activeReservation != null)
        {
            matchedDriver = activeReservation.Driver;
            matchedVehicle = await _dbContext.Vehicles
                .FirstOrDefaultAsync(v => v.DriverId == matchedDriver.Id && v.IsActive);
        }

        // Check matching attempts
        if (matchedDriver == null)
        {
            var attempt = await _dbContext.MatchingAttempts
                .Include(m => m.SelectedDriver).ThenInclude(d => d!.User)
                .Include(m => m.SelectedVehicle)
                .Where(m => m.RideRequest.Uuid == requestUuid)
                .OrderByDescending(m => m.AttemptNumber)
                .FirstOrDefaultAsync();

            if (attempt?.SelectedDriver != null)
            {
                matchedDriver = attempt.SelectedDriver;
                matchedVehicle = attempt.SelectedVehicle;
            }
        }

        // Downstream matching client fallback
        if (matchedDriver == null)
        {
            try
            {
                var matchingDto = await _driverMatchingClient.GetMatchingStatusAsync(requestUuid);
                if (matchingDto == null || !"MATCHED".Equals(matchingDto.Status, StringComparison.OrdinalIgnoreCase))
                {
                    matchingDto = await _driverMatchingClient.InitiateMatchingAsync(requestUuid, correlationId);
                }

                if (matchingDto != null && "MATCHED".Equals(matchingDto.Status, StringComparison.OrdinalIgnoreCase))
                {
                    if (matchingDto.Driver != null)
                    {
                        matchedDriver = await _dbContext.DriverProfiles
                            .Include(d => d.User)
                            .FirstOrDefaultAsync(d => d.User.Uuid == matchingDto.Driver.DriverUuid);
                    }
                    if (matchingDto.Vehicle != null)
                    {
                        matchedVehicle = await _dbContext.Vehicles
                            .FirstOrDefaultAsync(v => v.Uuid == matchingDto.Vehicle.VehicleUuid);
                    }
                }
            }
            catch (Exception ex)
            {
                _logger.LogWarning("Downstream driver matching call exception: {Message}", ex.Message);
            }
        }

        if (matchedDriver == null)
        {
            throw new ConflictException("No eligible driver has been matched or reserved for this ride request yet.");
        }

        if (matchedVehicle == null)
        {
            matchedVehicle = await _dbContext.Vehicles
                .FirstOrDefaultAsync(v => v.DriverId == matchedDriver.Id && v.IsActive)
                ?? throw new ConflictException("No active approved vehicle found for matched driver");
        }

        // 5. Concurrency check: Ensure driver has no conflicting active journeys
        var driverEngaged = await _dbContext.Bookings.AnyAsync(b =>
            b.DriverId == matchedDriver.Id &&
            (b.BookingStatus == BookingStatus.IN_PROGRESS || b.BookingStatus == BookingStatus.ARRIVED));

        if (driverEngaged)
        {
            throw new ConflictException("Matched driver is currently engaged in another active trip. Please retry.");
        }

        // 6. Generate secure 4-digit OTP
        var plainOtp = RandomNumberGenerator.GetInt32(0, 10000).ToString("D4");
        var hashedOtp = BCrypt.Net.BCrypt.HashPassword(plainOtp);
        var otpExpiry = DateTime.UtcNow.AddMinutes(_bookingSettings.OtpExpirationMinutes);

        // 7. Create persistent Booking record
        var booking = new Booking
        {
            Uuid = Guid.NewGuid().ToString(),
            RideRequestId = rideRequest.Id,
            RideRequest = rideRequest,
            RiderId = rider.Id,
            Rider = rider,
            DriverId = matchedDriver.Id,
            Driver = matchedDriver,
            VehicleId = matchedVehicle.Id,
            Vehicle = matchedVehicle,
            RideTypeId = rideRequest.RideTypeId,
            RideType = rideRequest.RideType,
            BookingStatus = BookingStatus.CONFIRMED,
            OtpHash = hashedOtp,
            OtpExpiresAt = otpExpiry,
            ScheduledTime = request.ScheduledTime,
            IdempotencyKey = !string.IsNullOrWhiteSpace(request.IdempotencyKey) ? request.IdempotencyKey.Trim() : null,
            PickupAddress = rideRequest.PickupAddress,
            PickupLatitude = rideRequest.PickupLatitude,
            PickupLongitude = rideRequest.PickupLongitude,
            DropAddress = rideRequest.DropAddress,
            DropLatitude = rideRequest.DropLatitude,
            DropLongitude = rideRequest.DropLongitude,
            EstimatedDistanceKm = rideRequest.EstimatedDistanceKm,
            EstimatedDurationMinutes = rideRequest.EstimatedDurationMinutes,
            TotalFare = rideRequest.EstimatedFare,
            Currency = "INR",
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        _dbContext.Bookings.Add(booking);
        await _dbContext.SaveChangesAsync();

        // 8. Create linked Ride entity
        var ride = new Ride
        {
            Uuid = Guid.NewGuid().ToString(),
            BookingId = booking.Id,
            Booking = booking,
            RiderId = rider.Id,
            Rider = rider,
            DriverId = matchedDriver.Id,
            Driver = matchedDriver,
            VehicleId = matchedVehicle.Id,
            Vehicle = matchedVehicle,
            RideTypeId = rideRequest.RideTypeId,
            RideType = rideRequest.RideType,
            PickupAddress = rideRequest.PickupAddress,
            PickupLatitude = rideRequest.PickupLatitude,
            PickupLongitude = rideRequest.PickupLongitude,
            DropAddress = rideRequest.DropAddress,
            DropLatitude = rideRequest.DropLatitude,
            DropLongitude = rideRequest.DropLongitude,
            Status = RideStatus.DRIVER_ASSIGNED,
            RequestedAt = booking.CreatedAt,
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        _dbContext.Rides.Add(ride);
        await _dbContext.SaveChangesAsync();

        // 9. Publish event
        await _eventPublisher.PublishAsync(new BookingEvent
        {
            EventType = "BOOKING_CREATED",
            BookingUuid = booking.Uuid,
            RideRequestUuid = requestUuid,
            RiderUserUuid = rider.User.Uuid,
            DriverUserUuid = matchedDriver.User.Uuid,
            Status = booking.BookingStatus.ToString(),
            Metadata = new Dictionary<string, object>
            {
                { "riderId", rider.Id },
                { "driverId", matchedDriver.Id },
                { "vehicleId", matchedVehicle.Id }
            }
        });

        return MapToResponse(booking, plainOtp);
    }

    public async Task<PageResponse<BookingResponse>> GetMyBookingsAsync(
        string riderUserUuid,
        BookingStatus? status,
        int page,
        int size)
    {
        var rider = await GetRiderOrThrowAsync(riderUserUuid);

        var query = GetBookingQuery().Where(b => b.RiderId == rider.Id);
        if (status.HasValue)
        {
            query = query.Where(b => b.BookingStatus == status.Value);
        }

        var totalElements = await query.LongCountAsync();
        var bookings = await query
            .OrderByDescending(b => b.CreatedAt)
            .Skip(page * size)
            .Take(size)
            .ToListAsync();

        var content = bookings.Select(b => MapToResponse(b)).ToList();
        return PageResponse<BookingResponse>.Create(content, page, size, totalElements);
    }

    public async Task<BookingResponse> GetMyBookingByUuidAsync(string riderUserUuid, string bookingUuid)
    {
        var rider = await GetRiderOrThrowAsync(riderUserUuid);
        var booking = await GetBookingOrThrowAsync(bookingUuid);

        if (booking.RiderId != rider.Id)
        {
            throw new ForbiddenException("Access denied: You do not own this booking");
        }

        return MapToResponse(booking);
    }

    public async Task<BookingResponse> CancelBookingByRiderAsync(
        string riderUserUuid,
        string bookingUuid,
        CancelBookingDto? request)
    {
        var rider = await GetRiderOrThrowAsync(riderUserUuid);
        var booking = await GetBookingOrThrowAsync(bookingUuid);

        if (booking.RiderId != rider.Id)
        {
            throw new ForbiddenException("Access denied: You do not own this booking");
        }

        // Idempotency: if already cancelled by rider, return existing
        if (booking.BookingStatus == BookingStatus.CANCELLED_BY_RIDER)
        {
            return MapToResponse(booking);
        }

        if (booking.BookingStatus == BookingStatus.COMPLETED)
        {
            throw new ConflictException("Cannot cancel a completed trip");
        }

        if (booking.BookingStatus == BookingStatus.IN_PROGRESS)
        {
            throw new ConflictException("Cannot cancel a trip that is already in progress");
        }

        if (!booking.BookingStatus.CanRiderCancel())
        {
            throw new ConflictException($"Booking cannot be cancelled in state: {booking.BookingStatus}");
        }

        booking.BookingStatus = BookingStatus.CANCELLED_BY_RIDER;
        booking.CancelledAt = DateTime.UtcNow;
        booking.CancelledBy = "RIDER";
        booking.CancellationReason = !string.IsNullOrWhiteSpace(request?.Reason) ? request.Reason.Trim() : "Cancelled by rider";
        booking.UpdatedAt = DateTime.UtcNow;

        var ride = await _dbContext.Rides.FirstOrDefaultAsync(r => r.BookingId == booking.Id);
        if (ride != null)
        {
            ride.Status = RideStatus.CANCELLED;
            ride.CancelledAt = DateTime.UtcNow;
            ride.UpdatedAt = DateTime.UtcNow;
        }

        await ReleaseReservationAsync(booking.RideRequestId);
        await _dbContext.SaveChangesAsync();

        await _eventPublisher.PublishAsync(new BookingEvent
        {
            EventType = "BOOKING_CANCELLED",
            BookingUuid = booking.Uuid,
            RideRequestUuid = booking.RideRequest.Uuid,
            RiderUserUuid = rider.User.Uuid,
            DriverUserUuid = booking.Driver.User.Uuid,
            Status = booking.BookingStatus.ToString(),
            Metadata = new Dictionary<string, object>
            {
                { "cancelledBy", "RIDER" },
                { "reason", booking.CancellationReason ?? string.Empty }
            }
        });

        return MapToResponse(booking);
    }

    // =========================================================================
    // DRIVER OPERATIONS
    // =========================================================================

    public async Task<PageResponse<BookingResponse>> GetDriverBookingsAsync(
        string driverUserUuid,
        BookingStatus? status,
        int page,
        int size)
    {
        var driver = await GetDriverOrThrowAsync(driverUserUuid);

        var query = GetBookingQuery().Where(b => b.DriverId == driver.Id);
        if (status.HasValue)
        {
            query = query.Where(b => b.BookingStatus == status.Value);
        }

        var totalElements = await query.LongCountAsync();
        var bookings = await query
            .OrderByDescending(b => b.CreatedAt)
            .Skip(page * size)
            .Take(size)
            .ToListAsync();

        var content = bookings.Select(b => MapToResponse(b)).ToList();
        return PageResponse<BookingResponse>.Create(content, page, size, totalElements);
    }

    public async Task<BookingResponse> GetDriverBookingByUuidAsync(string driverUserUuid, string bookingUuid)
    {
        var driver = await GetDriverOrThrowAsync(driverUserUuid);
        var booking = await GetBookingOrThrowAsync(bookingUuid);

        if (booking.DriverId != driver.Id)
        {
            throw new ForbiddenException("Access denied: You are not the assigned driver for this booking");
        }

        return MapToResponse(booking);
    }

    public async Task<BookingResponse> AcceptBookingByDriverAsync(string driverUserUuid, string bookingUuid)
    {
        var driver = await GetDriverOrThrowAsync(driverUserUuid);
        var booking = await GetBookingOrThrowAsync(bookingUuid);

        if (booking.DriverId != driver.Id)
        {
            throw new ForbiddenException("Access denied: You are not the assigned driver for this booking");
        }

        // Idempotency check
        if (booking.BookingStatus == BookingStatus.ACCEPTED)
        {
            return MapToResponse(booking);
        }

        if (!booking.BookingStatus.CanDriverAccept())
        {
            throw new ConflictException($"Cannot accept booking in current status: {booking.BookingStatus}");
        }

        booking.BookingStatus = BookingStatus.ACCEPTED;
        booking.AcceptedAt = DateTime.UtcNow;
        booking.UpdatedAt = DateTime.UtcNow;

        // Confirm active reservation
        var reservation = await _dbContext.DriverReservations
            .FirstOrDefaultAsync(r => r.RideRequestId == booking.RideRequestId &&
                                      r.Status == ReservationStatus.RESERVED &&
                                      r.ExpiresAt > DateTime.UtcNow);
        if (reservation != null)
        {
            reservation.Status = ReservationStatus.CONFIRMED;
            reservation.UpdatedAt = DateTime.UtcNow;
        }

        await _dbContext.SaveChangesAsync();

        await _eventPublisher.PublishAsync(new BookingEvent
        {
            EventType = "BOOKING_ACCEPTED",
            BookingUuid = booking.Uuid,
            RideRequestUuid = booking.RideRequest.Uuid,
            RiderUserUuid = booking.Rider.User.Uuid,
            DriverUserUuid = driver.User.Uuid,
            Status = booking.BookingStatus.ToString()
        });

        return MapToResponse(booking);
    }

    public async Task<BookingResponse> DeclineBookingByDriverAsync(
        string driverUserUuid,
        string bookingUuid,
        DeclineBookingDto? request)
    {
        var driver = await GetDriverOrThrowAsync(driverUserUuid);
        var booking = await GetBookingOrThrowAsync(bookingUuid);

        if (booking.DriverId != driver.Id)
        {
            throw new ForbiddenException("Access denied: You are not the assigned driver for this booking");
        }

        // Idempotency check
        if (booking.BookingStatus == BookingStatus.DECLINED_BY_DRIVER)
        {
            return MapToResponse(booking);
        }

        if (booking.BookingStatus == BookingStatus.IN_PROGRESS || booking.BookingStatus == BookingStatus.COMPLETED)
        {
            throw new ConflictException("Cannot decline a trip that has started or completed");
        }

        if (!booking.BookingStatus.CanDriverDecline())
        {
            throw new ConflictException($"Cannot decline booking in status: {booking.BookingStatus}");
        }

        booking.BookingStatus = BookingStatus.DECLINED_BY_DRIVER;
        booking.CancelledBy = "DRIVER";
        booking.CancellationReason = !string.IsNullOrWhiteSpace(request?.Reason) ? request.Reason.Trim() : "Declined by driver";
        booking.CancelledAt = DateTime.UtcNow;
        booking.UpdatedAt = DateTime.UtcNow;

        await ReleaseReservationAsync(booking.RideRequestId);
        await _dbContext.SaveChangesAsync();

        await _eventPublisher.PublishAsync(new BookingEvent
        {
            EventType = "BOOKING_DECLINED",
            BookingUuid = booking.Uuid,
            RideRequestUuid = booking.RideRequest.Uuid,
            RiderUserUuid = booking.Rider.User.Uuid,
            DriverUserUuid = driver.User.Uuid,
            Status = booking.BookingStatus.ToString(),
            Metadata = new Dictionary<string, object>
            {
                { "reason", booking.CancellationReason ?? string.Empty }
            }
        });

        return MapToResponse(booking);
    }

    public async Task<BookingResponse> ArriveBookingByDriverAsync(string driverUserUuid, string bookingUuid)
    {
        var driver = await GetDriverOrThrowAsync(driverUserUuid);
        var booking = await GetBookingOrThrowAsync(bookingUuid);

        if (booking.DriverId != driver.Id)
        {
            throw new ForbiddenException("Access denied: You are not the assigned driver for this booking");
        }

        // Idempotency check
        if (booking.BookingStatus == BookingStatus.ARRIVED)
        {
            return MapToResponse(booking);
        }

        if (!booking.BookingStatus.CanDriverArrive())
        {
            throw new ConflictException($"Cannot record arrival in status: {booking.BookingStatus}");
        }

        booking.BookingStatus = BookingStatus.ARRIVED;
        booking.ArrivedAt = DateTime.UtcNow;
        booking.UpdatedAt = DateTime.UtcNow;

        var ride = await _dbContext.Rides.FirstOrDefaultAsync(r => r.BookingId == booking.Id);
        if (ride != null)
        {
            ride.Status = RideStatus.DRIVER_ARRIVED;
            ride.UpdatedAt = DateTime.UtcNow;
        }

        await _dbContext.SaveChangesAsync();

        await _eventPublisher.PublishAsync(new BookingEvent
        {
            EventType = "DRIVER_ARRIVED",
            BookingUuid = booking.Uuid,
            RideRequestUuid = booking.RideRequest.Uuid,
            RiderUserUuid = booking.Rider.User.Uuid,
            DriverUserUuid = driver.User.Uuid,
            Status = booking.BookingStatus.ToString()
        });

        return MapToResponse(booking);
    }

    public async Task<BookingResponse> StartTripByDriverAsync(
        string driverUserUuid,
        string bookingUuid,
        StartTripDto? request)
    {
        var driver = await GetDriverOrThrowAsync(driverUserUuid);
        var booking = await GetBookingOrThrowAsync(bookingUuid);

        if (booking.DriverId != driver.Id)
        {
            throw new ForbiddenException("Access denied: You are not the assigned driver for this booking");
        }

        // Idempotency check
        if (booking.BookingStatus == BookingStatus.IN_PROGRESS)
        {
            return MapToResponse(booking);
        }

        if (!booking.BookingStatus.CanDriverStart())
        {
            throw new ConflictException($"Cannot start trip in current status: {booking.BookingStatus}");
        }

        // Validate OTP if provided
        if (!string.IsNullOrWhiteSpace(request?.Otp))
        {
            if (!BCrypt.Net.BCrypt.Verify(request.Otp.Trim(), booking.OtpHash))
            {
                throw new BadRequestException("Invalid OTP provided. Please request the 4-digit code from the rider.");
            }
            booking.OtpVerifiedAt = DateTime.UtcNow;
        }

        booking.BookingStatus = BookingStatus.IN_PROGRESS;
        booking.StartedAt = DateTime.UtcNow;
        booking.UpdatedAt = DateTime.UtcNow;

        var ride = await _dbContext.Rides.FirstOrDefaultAsync(r => r.BookingId == booking.Id);
        if (ride != null)
        {
            ride.Status = RideStatus.TRIP_STARTED;
            ride.ActualPickupTime = DateTime.UtcNow;
            ride.StartedAt = DateTime.UtcNow;
            ride.UpdatedAt = DateTime.UtcNow;
        }

        await _dbContext.SaveChangesAsync();

        await _eventPublisher.PublishAsync(new BookingEvent
        {
            EventType = "TRIP_STARTED",
            BookingUuid = booking.Uuid,
            RideRequestUuid = booking.RideRequest.Uuid,
            RiderUserUuid = booking.Rider.User.Uuid,
            DriverUserUuid = driver.User.Uuid,
            Status = booking.BookingStatus.ToString(),
            Metadata = new Dictionary<string, object>
            {
                { "otpVerified", booking.OtpVerifiedAt != null }
            }
        });

        return MapToResponse(booking);
    }

    public async Task<BookingResponse> CompleteTripByDriverAsync(
        string driverUserUuid,
        string bookingUuid,
        CompleteTripDto? request)
    {
        var driver = await GetDriverOrThrowAsync(driverUserUuid);
        var booking = await GetBookingOrThrowAsync(bookingUuid);

        if (booking.DriverId != driver.Id)
        {
            throw new ForbiddenException("Access denied: You are not the assigned driver for this booking");
        }

        // Idempotency check
        if (booking.BookingStatus == BookingStatus.COMPLETED)
        {
            return MapToResponse(booking);
        }

        if (!booking.BookingStatus.CanDriverComplete())
        {
            throw new ConflictException($"Cannot complete trip in status: {booking.BookingStatus}. Trip must be IN_PROGRESS.");
        }

        booking.BookingStatus = BookingStatus.COMPLETED;
        booking.CompletedAt = DateTime.UtcNow;
        booking.UpdatedAt = DateTime.UtcNow;

        if (request?.ActualDistanceKm != null)
        {
            booking.EstimatedDistanceKm = request.ActualDistanceKm;
        }
        if (request?.ActualDurationMinutes != null)
        {
            booking.EstimatedDurationMinutes = request.ActualDurationMinutes;
        }

        var ride = await _dbContext.Rides.FirstOrDefaultAsync(r => r.BookingId == booking.Id);
        if (ride != null)
        {
            ride.Status = RideStatus.TRIP_COMPLETED;
            ride.ActualDropTime = DateTime.UtcNow;
            ride.CompletedAt = DateTime.UtcNow;
            if (request?.ActualDistanceKm != null)
            {
                ride.ActualDistanceKm = request.ActualDistanceKm;
            }
            if (request?.ActualDurationMinutes != null)
            {
                ride.ActualDurationMinutes = request.ActualDurationMinutes;
            }
            ride.UpdatedAt = DateTime.UtcNow;
        }

        await ReleaseReservationAsync(booking.RideRequestId);
        await _dbContext.SaveChangesAsync();

        await _eventPublisher.PublishAsync(new BookingEvent
        {
            EventType = "TRIP_COMPLETED",
            BookingUuid = booking.Uuid,
            RideRequestUuid = booking.RideRequest.Uuid,
            RiderUserUuid = booking.Rider.User.Uuid,
            DriverUserUuid = driver.User.Uuid,
            Status = booking.BookingStatus.ToString(),
            Metadata = new Dictionary<string, object>
            {
                { "fare", booking.TotalFare ?? 0m }
            }
        });

        return MapToResponse(booking);
    }

    // =========================================================================
    // ADMIN OPERATIONS
    // =========================================================================

    public async Task<PageResponse<BookingResponse>> GetAdminBookingsAsync(
        long? riderId,
        long? driverId,
        BookingStatus? status,
        int page,
        int size)
    {
        var query = GetBookingQuery();

        if (riderId.HasValue)
        {
            query = query.Where(b => b.RiderId == riderId.Value);
        }
        if (driverId.HasValue)
        {
            query = query.Where(b => b.DriverId == driverId.Value);
        }
        if (status.HasValue)
        {
            query = query.Where(b => b.BookingStatus == status.Value);
        }

        var totalElements = await query.LongCountAsync();
        var bookings = await query
            .OrderByDescending(b => b.CreatedAt)
            .Skip(page * size)
            .Take(size)
            .ToListAsync();

        var content = bookings.Select(b => MapToResponse(b)).ToList();
        return PageResponse<BookingResponse>.Create(content, page, size, totalElements);
    }

    public async Task<BookingResponse> GetAdminBookingByUuidAsync(string bookingUuid)
    {
        var booking = await GetBookingOrThrowAsync(bookingUuid);
        return MapToResponse(booking);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private IQueryable<Booking> GetBookingQuery()
    {
        return _dbContext.Bookings
            .Include(b => b.RideRequest)
            .Include(b => b.Rider).ThenInclude(r => r.User)
            .Include(b => b.Driver).ThenInclude(d => d.User)
            .Include(b => b.Vehicle)
            .Include(b => b.RideType);
    }

    private async Task<RiderProfile> GetRiderOrThrowAsync(string userUuid)
    {
        if (string.IsNullOrWhiteSpace(userUuid))
        {
            throw new ForbiddenException("Authentication is required");
        }

        return await _dbContext.RiderProfiles
            .Include(r => r.User)
            .FirstOrDefaultAsync(r => r.User.Uuid == userUuid)
            ?? throw new ResourceNotFoundException($"Rider profile not found for user: {userUuid}");
    }

    private async Task<DriverProfile> GetDriverOrThrowAsync(string userUuid)
    {
        if (string.IsNullOrWhiteSpace(userUuid))
        {
            throw new ForbiddenException("Authentication is required");
        }

        return await _dbContext.DriverProfiles
            .Include(d => d.User)
            .FirstOrDefaultAsync(d => d.User.Uuid == userUuid)
            ?? throw new ResourceNotFoundException($"Driver profile not found for user: {userUuid}");
    }

    private async Task<Booking> GetBookingOrThrowAsync(string bookingUuid)
    {
        return await GetBookingQuery()
            .FirstOrDefaultAsync(b => b.Uuid == bookingUuid)
            ?? throw new ResourceNotFoundException($"Booking not found with UUID: {bookingUuid}");
    }

    private async Task ReleaseReservationAsync(long rideRequestId)
    {
        try
        {
            var reservation = await _dbContext.DriverReservations
                .FirstOrDefaultAsync(r => r.RideRequestId == rideRequestId &&
                                          r.Status == ReservationStatus.RESERVED);
            if (reservation != null)
            {
                reservation.Status = ReservationStatus.RELEASED;
                reservation.UpdatedAt = DateTime.UtcNow;
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning("Could not release driver reservation for ride request id {Id}: {Message}", rideRequestId, ex.Message);
        }
    }

    private static BookingResponse MapToResponse(Booking booking, string? plainOtp = null)
    {
        RiderSummaryDto? riderSummary = null;
        if (booking.Rider?.User != null)
        {
            riderSummary = new RiderSummaryDto
            {
                RiderUuid = booking.Rider.User.Uuid,
                FirstName = booking.Rider.User.FirstName,
                LastName = booking.Rider.User.LastName,
                Email = booking.Rider.User.Email,
                PhoneNumber = booking.Rider.User.PhoneNumber,
                Rating = booking.Rider.Rating
            };
        }

        DriverSummaryDto? driverSummary = null;
        if (booking.Driver?.User != null)
        {
            driverSummary = new DriverSummaryDto
            {
                DriverUuid = booking.Driver.User.Uuid,
                FirstName = booking.Driver.User.FirstName,
                LastName = booking.Driver.User.LastName,
                PhoneNumber = booking.Driver.User.PhoneNumber,
                Rating = booking.Driver.Rating,
                TotalTrips = booking.Driver.TotalTrips
            };
        }

        VehicleSummaryDto? vehicleSummary = null;
        if (booking.Vehicle != null)
        {
            vehicleSummary = new VehicleSummaryDto
            {
                VehicleUuid = booking.Vehicle.Uuid,
                Make = booking.Vehicle.Make,
                Model = booking.Vehicle.Model,
                Year = booking.Vehicle.Year,
                Color = booking.Vehicle.Color,
                LicensePlate = booking.Vehicle.LicensePlate
            };
        }

        RideTypeSummaryDto? rideTypeSummary = null;
        if (booking.RideType != null)
        {
            rideTypeSummary = new RideTypeSummaryDto
            {
                RideTypeUuid = booking.RideType.Uuid,
                Code = booking.RideType.Code,
                Name = booking.RideType.Name
            };
        }

        return new BookingResponse
        {
            BookingUuid = booking.Uuid,
            RideRequestUuid = booking.RideRequest?.Uuid ?? string.Empty,
            Status = booking.BookingStatus,
            Rider = riderSummary,
            Driver = driverSummary,
            Vehicle = vehicleSummary,
            RideType = rideTypeSummary,
            PickupAddress = booking.PickupAddress,
            PickupLatitude = booking.PickupLatitude,
            PickupLongitude = booking.PickupLongitude,
            DropAddress = booking.DropAddress,
            DropLatitude = booking.DropLatitude,
            DropLongitude = booking.DropLongitude,
            EstimatedDistanceKm = booking.EstimatedDistanceKm,
            EstimatedDurationMinutes = booking.EstimatedDurationMinutes,
            TotalFare = booking.TotalFare,
            Currency = booking.Currency ?? "INR",
            Otp = plainOtp,
            OtpVerified = booking.OtpVerifiedAt != null,
            ScheduledTime = booking.ScheduledTime,
            CancellationReason = booking.CancellationReason,
            CancelledBy = booking.CancelledBy,
            AcceptedAt = booking.AcceptedAt,
            ArrivedAt = booking.ArrivedAt,
            StartedAt = booking.StartedAt,
            CompletedAt = booking.CompletedAt,
            CancelledAt = booking.CancelledAt,
            CreatedAt = booking.CreatedAt,
            UpdatedAt = booking.UpdatedAt
        };
    }
}
