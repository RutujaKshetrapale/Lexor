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
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Moq;
using Xunit;

namespace BookingService.Tests;

public class BookingServiceTests
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

    public BookingServiceTests()
    {
        _dbContext = TestDbContextFactory.Create(Guid.NewGuid().ToString());

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
            Uuid = "rider-user-uuid-1",
            Email = "rider@lexor.com",
            PasswordHash = "hash",
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
            Uuid = "driver-user-uuid-2",
            Email = "driver@lexor.com",
            PasswordHash = "hash",
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
            Uuid = "veh-uuid-1",
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
            Uuid = "rt-uuid-1",
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
            Uuid = "req-uuid-1",
            RiderId = 1,
            Rider = _riderProfile,
            RideTypeId = 1,
            RideType = _rideType,
            PickupAddress = "Indiranagar 100ft Rd",
            PickupLatitude = 12.9784m,
            PickupLongitude = 77.6408m,
            DropAddress = "Prestige Tech Park",
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
            AttemptUuid = "att-uuid-1",
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

        // Active driver reservation
        var reservation = new DriverReservation
        {
            Id = 1,
            ReservationUuid = "res-uuid-1",
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
        _dbContext.DriverReservations.Add(reservation);

        _dbContext.SaveChanges();
    }

    [Fact]
    public async Task CreateBookingAsync_ValidRequest_Success()
    {
        var request = new CreateBookingDto
        {
            RideRequestUuid = "req-uuid-1",
            IdempotencyKey = "idemp-001"
        };

        var response = await _bookingService.CreateBookingAsync("rider-user-uuid-1", request);

        Assert.NotNull(response);
        Assert.Equal(BookingStatus.CONFIRMED, response.Status);
        Assert.NotNull(response.Otp);
        Assert.Equal(4, response.Otp.Length);
        Assert.Equal("req-uuid-1", response.RideRequestUuid);
        Assert.Equal("Indiranagar 100ft Rd", response.PickupAddress);

        // Verify database persistence
        var saved = _dbContext.Bookings.FirstOrDefault(b => b.Uuid == response.BookingUuid);
        Assert.NotNull(saved);
        Assert.Equal("idemp-001", saved.IdempotencyKey);
        Assert.True(BCrypt.Net.BCrypt.Verify(response.Otp, saved.OtpHash));

        // Verify linked ride created
        var ride = _dbContext.Rides.FirstOrDefault(r => r.BookingId == saved.Id);
        Assert.NotNull(ride);
        Assert.Equal(RideStatus.DRIVER_ASSIGNED, ride.Status);

        // Verify event published
        _eventPublisherMock.Verify(e => e.PublishAsync(It.Is<BookingEvent>(ev => ev.EventType == "BOOKING_CREATED")), Times.Once);
    }

    [Fact]
    public async Task CreateBookingAsync_IdempotentDuplicate_ReturnsExisting()
    {
        var request = new CreateBookingDto
        {
            RideRequestUuid = "req-uuid-1",
            IdempotencyKey = "idemp-dup"
        };

        var first = await _bookingService.CreateBookingAsync("rider-user-uuid-1", request);
        var second = await _bookingService.CreateBookingAsync("rider-user-uuid-1", request);

        Assert.Equal(first.BookingUuid, second.BookingUuid);
        Assert.Single(_dbContext.Bookings);
    }

    [Fact]
    public async Task CreateBookingAsync_RiderMismatch_ThrowsForbidden()
    {
        // Another rider tries to book rider-1's request
        var otherUser = new User
        {
            Id = 3,
            Uuid = "other-rider-uuid",
            Email = "other@lexor.com",
            PasswordHash = "hash",
            FirstName = "Other",
            LastName = "User",
            PhoneNumber = "+919876543299",
            Role = Role.RIDER,
            Status = UserStatus.ACTIVE
        };
        _dbContext.Users.Add(otherUser);
        _dbContext.RiderProfiles.Add(new RiderProfile { Id = 2, UserId = 3, User = otherUser });
        _dbContext.SaveChanges();

        var request = new CreateBookingDto { RideRequestUuid = "req-uuid-1" };

        await Assert.ThrowsAsync<ForbiddenException>(() =>
            _bookingService.CreateBookingAsync("other-rider-uuid", request));
    }

    [Fact]
    public async Task CreateBookingAsync_TerminalRequest_ThrowsConflict()
    {
        _rideRequest.Status = RideRequestStatus.CANCELLED;
        _dbContext.SaveChanges();

        var request = new CreateBookingDto { RideRequestUuid = "req-uuid-1" };

        await Assert.ThrowsAsync<ConflictException>(() =>
            _bookingService.CreateBookingAsync("rider-user-uuid-1", request));
    }

    [Fact]
    public async Task CreateBookingAsync_DriverEngaged_ThrowsConflict()
    {
        // Create an existing in-progress booking for driver 1
        var activeBooking = new Booking
        {
            Uuid = Guid.NewGuid().ToString(),
            RideRequestId = 1,
            RiderId = 1,
            DriverId = 1,
            VehicleId = 1,
            RideTypeId = 1,
            BookingStatus = BookingStatus.IN_PROGRESS,
            OtpHash = "hash"
        };
        _dbContext.Bookings.Add(activeBooking);
        _dbContext.SaveChanges();

        // Create a new ride request
        var newReq = new RideRequest
        {
            Id = 2,
            Uuid = "req-uuid-2",
            RiderId = 1,
            RideTypeId = 1,
            PickupAddress = "A",
            DropAddress = "B",
            Status = RideRequestStatus.MATCHED
        };
        _dbContext.RideRequests.Add(newReq);

        var newAttempt = new MatchingAttempt
        {
            Id = 2,
            AttemptUuid = "att-uuid-2",
            RideRequestId = 2,
            RideRequest = newReq,
            RideRequestUuid = newReq.Uuid,
            AttemptNumber = 1,
            Status = MatchingStatus.MATCHED,
            SelectedDriverId = 1,
            SelectedDriver = _driverProfile,
            SelectedVehicleId = 1,
            SelectedVehicle = _vehicle
        };
        _dbContext.MatchingAttempts.Add(newAttempt);

        _dbContext.DriverReservations.Add(new DriverReservation
        {
            Id = 2,
            ReservationUuid = "res-uuid-2",
            RideRequestId = 2,
            RideRequest = newReq,
            MatchingAttemptId = 2,
            MatchingAttempt = newAttempt,
            DriverId = 1,
            Driver = _driverProfile,
            Status = ReservationStatus.RESERVED,
            ExpiresAt = DateTime.UtcNow.AddMinutes(5),
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        });
        _dbContext.SaveChanges();

        var request = new CreateBookingDto { RideRequestUuid = "req-uuid-2" };

        await Assert.ThrowsAsync<ConflictException>(() =>
            _bookingService.CreateBookingAsync("rider-user-uuid-1", request));
    }

    [Fact]
    public async Task CancelBookingByRiderAsync_Success()
    {
        var createReq = new CreateBookingDto { RideRequestUuid = "req-uuid-1" };
        var booking = await _bookingService.CreateBookingAsync("rider-user-uuid-1", createReq);

        var cancelReq = new CancelBookingDto { Reason = "Change of travel plans" };
        var cancelled = await _bookingService.CancelBookingByRiderAsync("rider-user-uuid-1", booking.BookingUuid, cancelReq);

        Assert.Equal(BookingStatus.CANCELLED_BY_RIDER, cancelled.Status);
        Assert.Equal("Change of travel plans", cancelled.CancellationReason);
        Assert.Equal("RIDER", cancelled.CancelledBy);

        // Verify ride record also updated
        var ride = _dbContext.Rides.FirstOrDefault(r => r.Booking.Uuid == booking.BookingUuid);
        Assert.NotNull(ride);
        Assert.Equal(RideStatus.CANCELLED, ride.Status);

        // Verify reservation released
        var res = _dbContext.DriverReservations.Find(1L);
        Assert.NotNull(res);
        Assert.Equal(ReservationStatus.RELEASED, res.Status);
    }

    [Fact]
    public async Task CancelBookingByRiderAsync_AlreadyCancelled_Idempotent()
    {
        var createReq = new CreateBookingDto { RideRequestUuid = "req-uuid-1" };
        var booking = await _bookingService.CreateBookingAsync("rider-user-uuid-1", createReq);

        await _bookingService.CancelBookingByRiderAsync("rider-user-uuid-1", booking.BookingUuid, null);
        var repeat = await _bookingService.CancelBookingByRiderAsync("rider-user-uuid-1", booking.BookingUuid, null);

        Assert.Equal(BookingStatus.CANCELLED_BY_RIDER, repeat.Status);
    }

    [Fact]
    public async Task AcceptBookingByDriverAsync_Success()
    {
        var createReq = new CreateBookingDto { RideRequestUuid = "req-uuid-1" };
        var booking = await _bookingService.CreateBookingAsync("rider-user-uuid-1", createReq);

        var accepted = await _bookingService.AcceptBookingByDriverAsync("driver-user-uuid-2", booking.BookingUuid);

        Assert.Equal(BookingStatus.ACCEPTED, accepted.Status);
        Assert.NotNull(accepted.AcceptedAt);

        // Verify reservation confirmed
        var res = _dbContext.DriverReservations.Find(1L);
        Assert.NotNull(res);
        Assert.Equal(ReservationStatus.CONFIRMED, res.Status);
    }

    [Fact]
    public async Task AcceptBookingByDriverAsync_NotAssignedDriver_ThrowsForbidden()
    {
        var createReq = new CreateBookingDto { RideRequestUuid = "req-uuid-1" };
        var booking = await _bookingService.CreateBookingAsync("rider-user-uuid-1", createReq);

        // Add a different driver
        var otherDriverUser = new User
        {
            Id = 4,
            Uuid = "other-driver-uuid",
            Email = "otherdrv@lexor.com",
            PasswordHash = "hash",
            FirstName = "Other",
            LastName = "Driver",
            PhoneNumber = "+919876543200",
            Role = Role.DRIVER,
            Status = UserStatus.ACTIVE
        };
        _dbContext.Users.Add(otherDriverUser);
        _dbContext.DriverProfiles.Add(new DriverProfile { Id = 2, UserId = 4, User = otherDriverUser });
        _dbContext.SaveChanges();

        await Assert.ThrowsAsync<ForbiddenException>(() =>
            _bookingService.AcceptBookingByDriverAsync("other-driver-uuid", booking.BookingUuid));
    }

    [Fact]
    public async Task DeclineBookingByDriverAsync_Success()
    {
        var createReq = new CreateBookingDto { RideRequestUuid = "req-uuid-1" };
        var booking = await _bookingService.CreateBookingAsync("rider-user-uuid-1", createReq);

        var declineReq = new DeclineBookingDto { Reason = "Distance too far" };
        var declined = await _bookingService.DeclineBookingByDriverAsync("driver-user-uuid-2", booking.BookingUuid, declineReq);

        Assert.Equal(BookingStatus.DECLINED_BY_DRIVER, declined.Status);
        Assert.Equal("Distance too far", declined.CancellationReason);

        // Reservation released
        var res = _dbContext.DriverReservations.Find(1L);
        Assert.NotNull(res);
        Assert.Equal(ReservationStatus.RELEASED, res.Status);
    }

    [Fact]
    public async Task ArriveBookingByDriverAsync_Success()
    {
        var createReq = new CreateBookingDto { RideRequestUuid = "req-uuid-1" };
        var booking = await _bookingService.CreateBookingAsync("rider-user-uuid-1", createReq);
        await _bookingService.AcceptBookingByDriverAsync("driver-user-uuid-2", booking.BookingUuid);

        var arrived = await _bookingService.ArriveBookingByDriverAsync("driver-user-uuid-2", booking.BookingUuid);

        Assert.Equal(BookingStatus.ARRIVED, arrived.Status);
        Assert.NotNull(arrived.ArrivedAt);

        var ride = _dbContext.Rides.FirstOrDefault(r => r.Booking.Uuid == booking.BookingUuid);
        Assert.NotNull(ride);
        Assert.Equal(RideStatus.DRIVER_ARRIVED, ride.Status);
    }

    [Fact]
    public async Task StartTripByDriverAsync_ValidOtp_Success()
    {
        var createReq = new CreateBookingDto { RideRequestUuid = "req-uuid-1" };
        var booking = await _bookingService.CreateBookingAsync("rider-user-uuid-1", createReq);
        await _bookingService.AcceptBookingByDriverAsync("driver-user-uuid-2", booking.BookingUuid);
        await _bookingService.ArriveBookingByDriverAsync("driver-user-uuid-2", booking.BookingUuid);

        var startReq = new StartTripDto { Otp = booking.Otp };
        var started = await _bookingService.StartTripByDriverAsync("driver-user-uuid-2", booking.BookingUuid, startReq);

        Assert.Equal(BookingStatus.IN_PROGRESS, started.Status);
        Assert.NotNull(started.StartedAt);
        Assert.True(started.OtpVerified);

        var ride = _dbContext.Rides.FirstOrDefault(r => r.Booking.Uuid == booking.BookingUuid);
        Assert.NotNull(ride);
        Assert.Equal(RideStatus.TRIP_STARTED, ride.Status);
    }

    [Fact]
    public async Task StartTripByDriverAsync_InvalidOtp_ThrowsBadRequest()
    {
        var createReq = new CreateBookingDto { RideRequestUuid = "req-uuid-1" };
        var booking = await _bookingService.CreateBookingAsync("rider-user-uuid-1", createReq);
        await _bookingService.AcceptBookingByDriverAsync("driver-user-uuid-2", booking.BookingUuid);
        await _bookingService.ArriveBookingByDriverAsync("driver-user-uuid-2", booking.BookingUuid);

        var badStartReq = new StartTripDto { Otp = "9999" };

        await Assert.ThrowsAsync<BadRequestException>(() =>
            _bookingService.StartTripByDriverAsync("driver-user-uuid-2", booking.BookingUuid, badStartReq));
    }

    [Fact]
    public async Task CompleteTripByDriverAsync_Success()
    {
        var createReq = new CreateBookingDto { RideRequestUuid = "req-uuid-1" };
        var booking = await _bookingService.CreateBookingAsync("rider-user-uuid-1", createReq);
        await _bookingService.AcceptBookingByDriverAsync("driver-user-uuid-2", booking.BookingUuid);
        await _bookingService.ArriveBookingByDriverAsync("driver-user-uuid-2", booking.BookingUuid);
        await _bookingService.StartTripByDriverAsync("driver-user-uuid-2", booking.BookingUuid, new StartTripDto { Otp = booking.Otp });

        var completeReq = new CompleteTripDto
        {
            ActualDistanceKm = 11.8m,
            ActualDurationMinutes = 35
        };

        var completed = await _bookingService.CompleteTripByDriverAsync("driver-user-uuid-2", booking.BookingUuid, completeReq);

        Assert.Equal(BookingStatus.COMPLETED, completed.Status);
        Assert.NotNull(completed.CompletedAt);
        Assert.Equal(11.8m, completed.EstimatedDistanceKm);
        Assert.Equal(35, completed.EstimatedDurationMinutes);

        var ride = _dbContext.Rides.FirstOrDefault(r => r.Booking.Uuid == booking.BookingUuid);
        Assert.NotNull(ride);
        Assert.Equal(RideStatus.TRIP_COMPLETED, ride.Status);
        Assert.Equal(11.8m, ride.ActualDistanceKm);
    }

    [Fact]
    public async Task AdminEndpoints_FilterByStatusAndPagination_Success()
    {
        var createReq = new CreateBookingDto { RideRequestUuid = "req-uuid-1" };
        await _bookingService.CreateBookingAsync("rider-user-uuid-1", createReq);

        var result = await _bookingService.GetAdminBookingsAsync(null, null, BookingStatus.CONFIRMED, 0, 10);

        Assert.NotNull(result);
        Assert.Single(result.Content);
        Assert.Equal(1, result.TotalElements);
        Assert.True(result.First);
    }

    [Fact]
    public async Task StartTripByDriverAsync_EmptyOtp_ThrowsBadRequest()
    {
        var createReq = new CreateBookingDto { RideRequestUuid = "req-uuid-1" };
        var booking = await _bookingService.CreateBookingAsync("rider-user-uuid-1", createReq);
        await _bookingService.AcceptBookingByDriverAsync("driver-user-uuid-2", booking.BookingUuid);
        await _bookingService.ArriveBookingByDriverAsync("driver-user-uuid-2", booking.BookingUuid);

        var ex = await Assert.ThrowsAsync<BadRequestException>(() =>
            _bookingService.StartTripByDriverAsync("driver-user-uuid-2", booking.BookingUuid, new StartTripDto { Otp = "" }));

        Assert.Contains("OTP is required", ex.Message);
    }

    [Fact]
    public async Task StartTripByDriverAsync_ThreeInvalidAttempts_LocksTrip()
    {
        var createReq = new CreateBookingDto { RideRequestUuid = "req-uuid-1" };
        var booking = await _bookingService.CreateBookingAsync("rider-user-uuid-1", createReq);
        await _bookingService.AcceptBookingByDriverAsync("driver-user-uuid-2", booking.BookingUuid);
        await _bookingService.ArriveBookingByDriverAsync("driver-user-uuid-2", booking.BookingUuid);

        // Attempt 1: throws BadRequest
        var ex1 = await Assert.ThrowsAsync<BadRequestException>(() =>
            _bookingService.StartTripByDriverAsync("driver-user-uuid-2", booking.BookingUuid, new StartTripDto { Otp = "0000" }));
        Assert.Contains("Attempt 1 of 3", ex1.Message);

        // Attempt 2: throws BadRequest
        var ex2 = await Assert.ThrowsAsync<BadRequestException>(() =>
            _bookingService.StartTripByDriverAsync("driver-user-uuid-2", booking.BookingUuid, new StartTripDto { Otp = "0001" }));
        Assert.Contains("Attempt 2 of 3", ex2.Message);

        // Attempt 3: throws ConflictException (exceeded and locked)
        var ex3 = await Assert.ThrowsAsync<ConflictException>(() =>
            _bookingService.StartTripByDriverAsync("driver-user-uuid-2", booking.BookingUuid, new StartTripDto { Otp = "0002" }));
        Assert.Contains("Maximum attempts exceeded", ex3.Message);

        // Attempt 4 (even with valid OTP): locked
        var ex4 = await Assert.ThrowsAsync<ConflictException>(() =>
            _bookingService.StartTripByDriverAsync("driver-user-uuid-2", booking.BookingUuid, new StartTripDto { Otp = booking.Otp }));
        Assert.Contains("locked for safety", ex4.Message);
    }

    [Fact]
    public async Task AcceptBookingByDriverAsync_ConcurrentTwoDrivers_OnlyAssignedWins()
    {
        // Register second driver
        var driver2User = new User
        {
            Id = 99,
            Uuid = "driver-user-uuid-99",
            Email = "driver2@lexor.com",
            PasswordHash = "hash",
            FirstName = "Amit",
            LastName = "Patel",
            PhoneNumber = "+919876543299",
            Role = Role.DRIVER,
            Status = UserStatus.ACTIVE
        };
        _dbContext.Users.Add(driver2User);

        var driver2Profile = new DriverProfile
        {
            Id = 99,
            UserId = 99,
            User = driver2User,
            LicenseNumber = "DL-KA-01-2022-099",
            Status = DriverStatus.ONLINE,
            Rating = 4.88m,
            TotalTrips = 40
        };
        _dbContext.DriverProfiles.Add(driver2Profile);
        await _dbContext.SaveChangesAsync();

        var createReq = new CreateBookingDto { RideRequestUuid = "req-uuid-1" };
        var booking = await _bookingService.CreateBookingAsync("rider-user-uuid-1", createReq);

        // Simulate concurrent attempts to accept: driver-user-uuid-2 (assigned) vs driver-user-uuid-99 (unassigned)
        var taskAssigned = _bookingService.AcceptBookingByDriverAsync("driver-user-uuid-2", booking.BookingUuid);
        var taskUnassigned = _bookingService.AcceptBookingByDriverAsync("driver-user-uuid-99", booking.BookingUuid);

        var acceptedBooking = await taskAssigned;
        Assert.NotNull(acceptedBooking);
        Assert.Equal(BookingStatus.ACCEPTED, acceptedBooking.Status);

        var unassignedEx = await Assert.ThrowsAsync<ForbiddenException>(async () => await taskUnassigned);
        Assert.Contains("not the assigned driver", unassignedEx.Message);
    }
}
