using BookingService.Clients;
using BookingService.Clients.DTOs;
using BookingService.Configuration;
using BookingService.Data;
using BookingService.DTOs.Request;
using BookingService.Entities;
using BookingService.Enums;
using BookingService.Events;
using BookingService.Exceptions;
using BookingService.Services;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Moq;
using Xunit;

namespace BookingService.Tests;

public class BookingWorkflowEndToEndIntegrationTests
{
    private readonly LexorDbContext _dbContext;
    private readonly Mock<IRideRequestClient> _rideRequestClientMock = new();
    private readonly Mock<IDriverMatchingClient> _driverMatchingClientMock = new();
    private readonly Mock<IEventPublisher> _eventPublisherMock = new();
    private readonly Mock<ILogger<BookingService.Services.BookingService>> _loggerMock = new();
    private readonly IBookingService _bookingService;

    private readonly User _riderUser;
    private readonly RiderProfile _riderProfile;
    private readonly User _driverUser;
    private readonly DriverProfile _driverProfile;
    private readonly Vehicle _vehicle;
    private readonly RideType _rideType;
    private readonly RideRequest _rideRequest;
    private readonly MatchingAttempt _matchingAttempt;
    private readonly DriverReservation _reservation;

    public BookingWorkflowEndToEndIntegrationTests()
    {
        _dbContext = TestDbContextFactory.Create("lexor_e2e_" + Guid.NewGuid().ToString("N"));

        var bookingSettings = Options.Create(new BookingSettings
        {
            InternalApiKey = "test-internal-key",
            OtpExpirationMinutes = 15,
            ConnectTimeoutSeconds = 3,
            ReadTimeoutSeconds = 5
        });

        _bookingService = new BookingService.Services.BookingService(
            _dbContext,
            _rideRequestClientMock.Object,
            _driverMatchingClientMock.Object,
            _eventPublisherMock.Object,
            bookingSettings,
            _loggerMock.Object
        );

        // Seed domain data
        _riderUser = new User
        {
            Id = 1,
            Uuid = "rider-e2e-uuid-1",
            Email = "rajesh.rider@lexor.com",
            PasswordHash = "hashed_pw",
            FirstName = "Rajesh",
            LastName = "Kumar",
            PhoneNumber = "+919876543211",
            Role = Role.RIDER,
            Status = UserStatus.ACTIVE
        };
        _dbContext.Users.Add(_riderUser);

        _riderProfile = new RiderProfile
        {
            Id = 1,
            UserId = 1,
            User = _riderUser,
            Rating = 4.9m
        };
        _dbContext.RiderProfiles.Add(_riderProfile);

        _driverUser = new User
        {
            Id = 2,
            Uuid = "driver-e2e-uuid-2",
            Email = "vikram.driver@lexor.com",
            PasswordHash = "hashed_pw",
            FirstName = "Vikram",
            LastName = "Singh",
            PhoneNumber = "+919876543212",
            Role = Role.DRIVER,
            Status = UserStatus.ACTIVE
        };
        _dbContext.Users.Add(_driverUser);

        _driverProfile = new DriverProfile
        {
            Id = 1,
            UserId = 2,
            User = _driverUser,
            LicenseNumber = "DL-KA-01-2022-001",
            Status = DriverStatus.ONLINE,
            Rating = 4.95m,
            TotalTrips = 80
        };
        _dbContext.DriverProfiles.Add(_driverProfile);

        _vehicle = new Vehicle
        {
            Id = 1,
            Uuid = "veh-e2e-uuid-1",
            DriverId = 1,
            Driver = _driverProfile,
            Make = "Toyota",
            Model = "Hyryder",
            Year = 2023,
            Color = "Black",
            LicensePlate = "KA-01-EQ-4829",
            IsActive = true
        };
        _dbContext.Vehicles.Add(_vehicle);

        _rideType = new RideType
        {
            Id = 1,
            Uuid = "rt-e2e-uuid-1",
            Code = "GO",
            Name = "LEXOR Go",
            BaseFare = 50m,
            PerKmRate = 15m,
            PerMinuteRate = 2m
        };
        _dbContext.RideTypes.Add(_rideType);

        _rideRequest = new RideRequest
        {
            Id = 1,
            Uuid = "req-e2e-uuid-1",
            RiderId = 1,
            Rider = _riderProfile,
            RideTypeId = 1,
            RideType = _rideType,
            PickupAddress = "Indiranagar 100ft Rd, Bangalore",
            PickupLatitude = 12.9784m,
            PickupLongitude = 77.6408m,
            DropAddress = "Prestige Tech Park, Bangalore",
            DropLatitude = 12.9372m,
            DropLongitude = 77.6949m,
            EstimatedDistanceKm = 11.5m,
            EstimatedDurationMinutes = 32,
            EstimatedFare = 250m,
            Status = RideRequestStatus.MATCHED
        };
        _dbContext.RideRequests.Add(_rideRequest);

        _matchingAttempt = new MatchingAttempt
        {
            Id = 1,
            AttemptUuid = "att-e2e-uuid-1",
            RideRequestId = 1,
            RideRequest = _rideRequest,
            RideRequestUuid = _rideRequest.Uuid,
            AttemptNumber = 1,
            Status = MatchingStatus.MATCHED,
            SelectedDriverId = 1,
            SelectedDriver = _driverProfile,
            SelectedVehicleId = 1,
            SelectedVehicle = _vehicle
        };
        _dbContext.MatchingAttempts.Add(_matchingAttempt);

        _reservation = new DriverReservation
        {
            Id = 1,
            ReservationUuid = "res-e2e-uuid-1",
            RideRequestId = 1,
            RideRequest = _rideRequest,
            MatchingAttemptId = 1,
            MatchingAttempt = _matchingAttempt,
            DriverId = 1,
            Driver = _driverProfile,
            Status = ReservationStatus.RESERVED,
            ExpiresAt = DateTime.UtcNow.AddMinutes(5),
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };
        _dbContext.DriverReservations.Add(_reservation);

        _dbContext.SaveChanges();
    }

    [Fact]
    public async Task ExecuteEndToEndRideBookingLifecycle_AllPhases_StateConsistent()
    {
        // -------------------------------------------------------------
        // Step 1: Rider creates booking for matched ride request
        // -------------------------------------------------------------
        var createBookingDto = new CreateBookingDto
        {
            RideRequestUuid = _rideRequest.Uuid,
            IdempotencyKey = "idemp-e2e-lifecycle-1"
        };

        var booking = await _bookingService.CreateBookingAsync(_riderUser.Uuid, createBookingDto);

        Assert.NotNull(booking);
        Assert.Equal(BookingStatus.CONFIRMED, booking.Status);
        Assert.NotNull(booking.Otp);
        Assert.Equal(4, booking.Otp.Length);
        Assert.Equal("Vikram", booking.Driver?.FirstName);
        Assert.Equal("Hyryder", booking.Vehicle?.Model);
        Assert.Equal("KA-01-EQ-4829", booking.Vehicle?.LicensePlate);

        // Verify database persistence and linked ride record
        var bookingInDb = await _dbContext.Bookings.FirstOrDefaultAsync(b => b.Uuid == booking.BookingUuid);
        Assert.NotNull(bookingInDb);
        Assert.Equal("idemp-e2e-lifecycle-1", bookingInDb.IdempotencyKey);
        Assert.True(BCrypt.Net.BCrypt.Verify(booking.Otp, bookingInDb.OtpHash));

        var rideInDb = await _dbContext.Rides.FirstOrDefaultAsync(r => r.BookingId == bookingInDb.Id);
        Assert.NotNull(rideInDb);
        Assert.Equal(RideStatus.DRIVER_ASSIGNED, rideInDb.Status);

        // -------------------------------------------------------------
        // Step 2: Driver accepts booking offer
        // -------------------------------------------------------------
        var accepted = await _bookingService.AcceptBookingByDriverAsync(_driverUser.Uuid, booking.BookingUuid);

        Assert.NotNull(accepted);
        Assert.Equal(BookingStatus.ACCEPTED, accepted.Status);
        Assert.NotNull(accepted.AcceptedAt);

        // Verify driver reservation is confirmed
        var confirmedReservation = await _dbContext.DriverReservations.FindAsync(1L);
        Assert.NotNull(confirmedReservation);
        Assert.Equal(ReservationStatus.CONFIRMED, confirmedReservation.Status);

        // -------------------------------------------------------------
        // Step 3: Driver arrives at pickup
        // -------------------------------------------------------------
        var arrived = await _bookingService.ArriveBookingByDriverAsync(_driverUser.Uuid, booking.BookingUuid);

        Assert.NotNull(arrived);
        Assert.Equal(BookingStatus.ARRIVED, arrived.Status);
        Assert.NotNull(arrived.ArrivedAt);

        rideInDb = await _dbContext.Rides.FirstOrDefaultAsync(r => r.BookingId == bookingInDb.Id);
        Assert.NotNull(rideInDb);
        Assert.Equal(RideStatus.DRIVER_ARRIVED, rideInDb.Status);

        // -------------------------------------------------------------
        // Step 4: Driver attempts to start trip with invalid OTP -> rejected
        // -------------------------------------------------------------
        var invalidOtpReq = new StartTripDto { Otp = "9999" };
        await Assert.ThrowsAsync<BadRequestException>(() =>
            _bookingService.StartTripByDriverAsync(_driverUser.Uuid, booking.BookingUuid, invalidOtpReq));

        // -------------------------------------------------------------
        // Step 5: Driver starts trip with valid OTP -> IN_PROGRESS
        // -------------------------------------------------------------
        var validOtpReq = new StartTripDto { Otp = booking.Otp };
        var started = await _bookingService.StartTripByDriverAsync(_driverUser.Uuid, booking.BookingUuid, validOtpReq);

        Assert.NotNull(started);
        Assert.Equal(BookingStatus.IN_PROGRESS, started.Status);
        Assert.NotNull(started.StartedAt);
        Assert.True(started.OtpVerified);

        rideInDb = await _dbContext.Rides.FirstOrDefaultAsync(r => r.BookingId == bookingInDb.Id);
        Assert.NotNull(rideInDb);
        Assert.Equal(RideStatus.TRIP_STARTED, rideInDb.Status);

        // -------------------------------------------------------------
        // Step 6: Driver completes trip
        // -------------------------------------------------------------
        var completeReq = new CompleteTripDto
        {
            ActualDistanceKm = 11.8m,
            ActualDurationMinutes = 35
        };

        var completed = await _bookingService.CompleteTripByDriverAsync(_driverUser.Uuid, booking.BookingUuid, completeReq);

        Assert.NotNull(completed);
        Assert.Equal(BookingStatus.COMPLETED, completed.Status);
        Assert.NotNull(completed.CompletedAt);
        Assert.Equal(11.8m, completed.EstimatedDistanceKm);
        Assert.Equal(35, completed.EstimatedDurationMinutes);

        rideInDb = await _dbContext.Rides.FirstOrDefaultAsync(r => r.BookingId == bookingInDb.Id);
        Assert.NotNull(rideInDb);
        Assert.Equal(RideStatus.TRIP_COMPLETED, rideInDb.Status);
        Assert.Equal(11.8m, rideInDb.ActualDistanceKm);

        // -------------------------------------------------------------
        // Step 7: Rider views booking details and booking history
        // -------------------------------------------------------------
        var riderBooking = await _bookingService.GetMyBookingByUuidAsync(_riderUser.Uuid, booking.BookingUuid);
        Assert.NotNull(riderBooking);
        Assert.Equal(BookingStatus.COMPLETED, riderBooking.Status);

        var riderHistory = await _bookingService.GetMyBookingsAsync(_riderUser.Uuid, null, 0, 10);
        Assert.NotNull(riderHistory);
        Assert.Single(riderHistory.Content);
        Assert.Equal(booking.BookingUuid, riderHistory.Content[0].BookingUuid);

        // -------------------------------------------------------------
        // Step 8: Admin can inspect completed bookings
        // -------------------------------------------------------------
        var adminResult = await _bookingService.GetAdminBookingsAsync(null, null, BookingStatus.COMPLETED, 0, 10);
        Assert.NotNull(adminResult);
        Assert.Single(adminResult.Content);
        Assert.Equal(booking.BookingUuid, adminResult.Content[0].BookingUuid);
    }

    [Fact]
    public async Task Idempotency_DuplicateRequest_ReturnsSameBookingWithoutSideEffects()
    {
        var request = new CreateBookingDto
        {
            RideRequestUuid = _rideRequest.Uuid,
            IdempotencyKey = "idemp-e2e-dup-check"
        };

        var first = await _bookingService.CreateBookingAsync(_riderUser.Uuid, request);
        var second = await _bookingService.CreateBookingAsync(_riderUser.Uuid, request);

        Assert.Equal(first.BookingUuid, second.BookingUuid);
        Assert.Equal(first.Status, second.Status);

        var count = await _dbContext.Bookings.CountAsync(b => b.RideRequestId == _rideRequest.Id);
        Assert.Equal(1, count);
    }

    [Fact]
    public async Task ObjectLevelAuthorization_SeparatesRiderAndDriverPermissions()
    {
        var createBookingDto = new CreateBookingDto
        {
            RideRequestUuid = _rideRequest.Uuid,
            IdempotencyKey = "idemp-e2e-sec-check"
        };
        var booking = await _bookingService.CreateBookingAsync(_riderUser.Uuid, createBookingDto);

        // Add second rider
        var unauthorizedRider = new User
        {
            Id = 3,
            Uuid = "unauth-rider-uuid",
            Email = "stranger@lexor.com",
            PasswordHash = "hashed",
            FirstName = "Imposter",
            LastName = "Rider",
            PhoneNumber = "+919999999999",
            Role = Role.RIDER,
            Status = UserStatus.ACTIVE
        };
        _dbContext.Users.Add(unauthorizedRider);
        _dbContext.RiderProfiles.Add(new RiderProfile { Id = 2, UserId = 3, User = unauthorizedRider });

        // Add second driver
        var unauthorizedDriver = new User
        {
            Id = 4,
            Uuid = "unauth-driver-uuid",
            Email = "stranger.driver@lexor.com",
            PasswordHash = "hashed",
            FirstName = "Other",
            LastName = "Driver",
            PhoneNumber = "+918888888888",
            Role = Role.DRIVER,
            Status = UserStatus.ACTIVE
        };
        _dbContext.Users.Add(unauthorizedDriver);
        _dbContext.DriverProfiles.Add(new DriverProfile { Id = 2, UserId = 4, User = unauthorizedDriver, LicenseNumber = "DL-999" });
        await _dbContext.SaveChangesAsync();

        // 1. Unauthorized rider cannot view or cancel rider 1's booking
        await Assert.ThrowsAsync<ForbiddenException>(() =>
            _bookingService.GetMyBookingByUuidAsync("unauth-rider-uuid", booking.BookingUuid));

        await Assert.ThrowsAsync<ForbiddenException>(() =>
            _bookingService.CancelBookingByRiderAsync("unauth-rider-uuid", booking.BookingUuid, new CancelBookingDto { Reason = "Malicious" }));

        // 2. Unauthorized driver cannot accept or start booking
        await Assert.ThrowsAsync<ForbiddenException>(() =>
            _bookingService.AcceptBookingByDriverAsync("unauth-driver-uuid", booking.BookingUuid));

        await Assert.ThrowsAsync<ForbiddenException>(() =>
            _bookingService.StartTripByDriverAsync("unauth-driver-uuid", booking.BookingUuid, new StartTripDto { Otp = "1234" }));
    }

    [Fact]
    public async Task RiderCancellationLifecycle_ReleasesDriverReservation()
    {
        var createBookingDto = new CreateBookingDto
        {
            RideRequestUuid = _rideRequest.Uuid,
            IdempotencyKey = "idemp-e2e-cancel-1"
        };
        var booking = await _bookingService.CreateBookingAsync(_riderUser.Uuid, createBookingDto);

        var cancelReq = new CancelBookingDto { Reason = "Plans changed" };
        var cancelled = await _bookingService.CancelBookingByRiderAsync(_riderUser.Uuid, booking.BookingUuid, cancelReq);

        Assert.Equal(BookingStatus.CANCELLED_BY_RIDER, cancelled.Status);
        Assert.Equal("Plans changed", cancelled.CancellationReason);
        Assert.Equal("RIDER", cancelled.CancelledBy);

        // Verify driver reservation is released
        var reservationInDb = await _dbContext.DriverReservations.FindAsync(1L);
        Assert.NotNull(reservationInDb);
        Assert.Equal(ReservationStatus.RELEASED, reservationInDb.Status);

        // Verify linked ride is cancelled
        var rideInDb = await _dbContext.Rides.FirstOrDefaultAsync(r => r.Booking.Uuid == booking.BookingUuid);
        Assert.NotNull(rideInDb);
        Assert.Equal(RideStatus.CANCELLED, rideInDb.Status);
    }

    [Fact]
    public async Task DriverDeclineLifecycle_ReleasesDriverReservation()
    {
        var createBookingDto = new CreateBookingDto
        {
            RideRequestUuid = _rideRequest.Uuid,
            IdempotencyKey = "idemp-e2e-decline-1"
        };
        var booking = await _bookingService.CreateBookingAsync(_riderUser.Uuid, createBookingDto);

        var declineReq = new DeclineBookingDto { Reason = "Vehicle maintenance required" };
        var declined = await _bookingService.DeclineBookingByDriverAsync(_driverUser.Uuid, booking.BookingUuid, declineReq);

        Assert.Equal(BookingStatus.DECLINED_BY_DRIVER, declined.Status);
        Assert.Equal("Vehicle maintenance required", declined.CancellationReason);

        // Verify driver reservation is released
        var reservationInDb = await _dbContext.DriverReservations.FindAsync(1L);
        Assert.NotNull(reservationInDb);
        Assert.Equal(ReservationStatus.RELEASED, reservationInDb.Status);
    }

    [Fact]
    public async Task DoubleBookingProtection_DriverAlreadyEngaged_ThrowsConflict()
    {
        // Set up an active ongoing booking for driver 1
        var existingBooking = new Booking
        {
            Uuid = "active-trip-uuid",
            RideRequestId = 1,
            RiderId = 1,
            DriverId = 1,
            VehicleId = 1,
            RideTypeId = 1,
            BookingStatus = BookingStatus.IN_PROGRESS,
            OtpHash = "hash"
        };
        _dbContext.Bookings.Add(existingBooking);

        // Create a separate ride request attempting to book driver 1 again
        var secondRequest = new RideRequest
        {
            Id = 2,
            Uuid = "req-e2e-uuid-2",
            RiderId = 1,
            RideTypeId = 1,
            PickupAddress = "MG Road",
            DropAddress = "Whitefield",
            Status = RideRequestStatus.MATCHED
        };
        _dbContext.RideRequests.Add(secondRequest);

        var secondAttempt = new MatchingAttempt
        {
            Id = 2,
            AttemptUuid = "att-e2e-uuid-2",
            RideRequestId = 2,
            RideRequest = secondRequest,
            RideRequestUuid = secondRequest.Uuid,
            AttemptNumber = 1,
            Status = MatchingStatus.MATCHED,
            SelectedDriverId = 1,
            SelectedDriver = _driverProfile,
            SelectedVehicleId = 1,
            SelectedVehicle = _vehicle
        };
        _dbContext.MatchingAttempts.Add(secondAttempt);

        _dbContext.DriverReservations.Add(new DriverReservation
        {
            Id = 2,
            ReservationUuid = "res-e2e-uuid-2",
            RideRequestId = 2,
            RideRequest = secondRequest,
            MatchingAttemptId = 2,
            MatchingAttempt = secondAttempt,
            DriverId = 1,
            Driver = _driverProfile,
            Status = ReservationStatus.RESERVED,
            ExpiresAt = DateTime.UtcNow.AddMinutes(5),
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        });
        await _dbContext.SaveChangesAsync();

        var createBookingDto = new CreateBookingDto
        {
            RideRequestUuid = "req-e2e-uuid-2",
            IdempotencyKey = "idemp-double-booking-test"
        };

        await Assert.ThrowsAsync<ConflictException>(() =>
            _bookingService.CreateBookingAsync(_riderUser.Uuid, createBookingDto));
    }

    [Fact]
    public async Task DynamicFallback_CallsDriverMatchingClient_WhenNoLocalReservation()
    {
        // Create request 3 with no local matching attempt or reservation
        var thirdRequest = new RideRequest
        {
            Id = 3,
            Uuid = "req-e2e-remote-match",
            RiderId = 1,
            Rider = _riderProfile,
            RideTypeId = 1,
            RideType = _rideType,
            PickupAddress = "Koramangala 4th Block",
            DropAddress = "Electronic City",
            Status = RideRequestStatus.MATCHED
        };
        _dbContext.RideRequests.Add(thirdRequest);
        await _dbContext.SaveChangesAsync();

        // Configure downstream client to return matched attempt
        _driverMatchingClientMock
            .Setup(c => c.GetMatchingStatusAsync("req-e2e-remote-match"))
            .ReturnsAsync(new MatchingAttemptClientDto
            {
                AttemptUuid = "att-remote-1",
                RideRequestUuid = "req-e2e-remote-match",
                Status = "MATCHED",
                Driver = new DriverCandidateClientDto
                {
                    DriverUuid = _driverUser.Uuid
                },
                Vehicle = new VehicleClientDto
                {
                    VehicleUuid = _vehicle.Uuid
                }
            });

        var createBookingDto = new CreateBookingDto
        {
            RideRequestUuid = "req-e2e-remote-match",
            IdempotencyKey = "idemp-fallback-test"
        };

        var booking = await _bookingService.CreateBookingAsync(_riderUser.Uuid, createBookingDto);

        Assert.NotNull(booking);
        Assert.Equal(BookingStatus.CONFIRMED, booking.Status);
        Assert.Equal("req-e2e-remote-match", booking.RideRequestUuid);
        Assert.Equal("Vikram", booking.Driver?.FirstName);

        _driverMatchingClientMock.Verify(c => c.GetMatchingStatusAsync("req-e2e-remote-match"), Times.Once);
    }
}
