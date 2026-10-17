using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Moq;
using Xunit;
using DriverMatchingService.Configuration;
using DriverMatchingService.Data;
using DriverMatchingService.DTOs.Request;
using DriverMatchingService.Entities;
using DriverMatchingService.Enums;
using DriverMatchingService.Events;
using DriverMatchingService.Exceptions;
using DriverMatchingService.Services;

namespace DriverMatchingService.Tests;

public class DriverMatchingServiceTests
{
    private readonly LexorDbContext _dbContext;
    private readonly Mock<IDriverLocationService> _locationServiceMock = new();
    private readonly Mock<IScoringPolicyService> _scoringPolicyServiceMock = new();
    private readonly Mock<IEventPublisher> _eventPublisherMock = new();
    private readonly IDriverMatchingService _matchingService;

    private readonly RideRequest _validRideRequest;
    private readonly DriverProfile _eligibleDriver;
    private readonly Vehicle _eligibleVehicle;
    private readonly DriverVehicleAssignment _validAssignment;
    private readonly DriverLocation _freshLocation;

    public DriverMatchingServiceTests()
    {
        _dbContext = TestDbContextFactory.Create(Guid.NewGuid().ToString());

        var matchingSettings = Options.Create(new MatchingSettings
        {
            MaxRadiusKm = 10.0,
            MaxLocationAgeSeconds = 300,
            ReservationTtlSeconds = 120
        });

        var loggerMock = new Mock<ILogger<DriverMatchingService.Services.DriverMatchingService>>();

        _matchingService = new DriverMatchingService.Services.DriverMatchingService(
            _dbContext,
            _locationServiceMock.Object,
            _scoringPolicyServiceMock.Object,
            _eventPublisherMock.Object,
            matchingSettings,
            loggerMock.Object
        );

        // Seed Ride Type
        var rideType = new RideType
        {
            Id = 1,
            Uuid = "ride-type-go-uuid",
            Name = "LEXOR Go",
            Code = "GO",
            BaseFare = 50.0m,
            PerKmRate = 15.0m,
            PerMinuteRate = 2.5m,
            MinimumFare = 80.0m
        };
        _dbContext.RideTypes.Add(rideType);

        // Seed Ride Request
        _validRideRequest = new RideRequest
        {
            Id = 10,
            Uuid = "req-1111-2222-3333",
            RiderId = 1,
            RideTypeId = rideType.Id,
            RideType = rideType,
            PickupAddress = "MG Road Bengaluru",
            PickupLatitude = 12.97840000m,
            PickupLongitude = 77.64080000m,
            DropAddress = "Prestige Tech Park Bengaluru",
            DropLatitude = 12.93720000m,
            DropLongitude = 77.69490000m,
            EstimatedDistanceKm = 11.50m,
            EstimatedDurationMinutes = 32,
            EstimatedFare = 250.0m,
            Status = RideRequestStatus.REQUESTED
        };
        _dbContext.RideRequests.Add(_validRideRequest);

        // Seed Driver User and Profile
        var driverUser = new User
        {
            Id = 3,
            Uuid = "usr-3333-4444",
            FirstName = "Vikram",
            LastName = "Singh",
            Email = "vikram.singh@example.com",
            PhoneNumber = "+919876543212",
            PasswordHash = "hash",
            Role = Role.DRIVER,
            Status = UserStatus.ACTIVE
        };
        _dbContext.Users.Add(driverUser);

        _eligibleDriver = new DriverProfile
        {
            Id = 1,
            UserId = driverUser.Id,
            User = driverUser,
            LicenseNumber = "KA-01-20230009876",
            Status = DriverStatus.ONLINE,
            OnboardingStatus = OnboardingStatus.VERIFIED,
            IsVerified = true,
            BackgroundCheckStatus = BackgroundCheckStatus.PASSED,
            Rating = 4.90m,
            TotalTrips = 145,
            AcceptanceRate = 98.50m
        };
        _dbContext.DriverProfiles.Add(_eligibleDriver);

        // Seed Vehicle
        _eligibleVehicle = new Vehicle
        {
            Id = 1,
            Uuid = "veh-1111-2222",
            DriverId = _eligibleDriver.Id,
            Driver = _eligibleDriver,
            Make = "Toyota",
            Model = "Urban Cruiser Hyryder",
            Year = 2023,
            Color = "Midnight Black",
            LicensePlate = "KA-01-EQ-4829",
            RideTypeCategory = "GO",
            SeatCapacity = 4,
            FuelType = FuelType.HYBRID,
            IsActive = true,
            IsInspected = true,
            VerificationStatus = VehicleVerificationStatus.VERIFIED
        };
        _dbContext.Vehicles.Add(_eligibleVehicle);

        // Seed Assignment
        _validAssignment = new DriverVehicleAssignment
        {
            Id = 1,
            DriverId = _eligibleDriver.Id,
            Driver = _eligibleDriver,
            VehicleId = _eligibleVehicle.Id,
            Vehicle = _eligibleVehicle,
            Status = AssignmentStatus.ACTIVE
        };
        _dbContext.DriverVehicleAssignments.Add(_validAssignment);

        _freshLocation = new DriverLocation
        {
            Id = 50,
            DriverId = _eligibleDriver.Id,
            Latitude = 12.97500000m,
            Longitude = 77.64500000m,
            RecordedAt = DateTime.UtcNow
        };

        _dbContext.SaveChanges();
    }

    [Fact]
    public async Task Match_EligibleDriver_SucceedsAndCreatesReservation()
    {
        _locationServiceMock.Setup(l => l.GetLatestLocationAsync(1))
            .ReturnsAsync(_freshLocation);
        _locationServiceMock.Setup(l => l.IsLocationFresh(_freshLocation, 300))
            .Returns(true);
        _scoringPolicyServiceMock.Setup(s => s.CalculateHaversineDistanceKm(It.IsAny<double>(), It.IsAny<double>(), It.IsAny<double>(), It.IsAny<double>()))
            .Returns(1.50);
        _scoringPolicyServiceMock.Setup(s => s.CalculateMatchScore(_eligibleDriver, 1.50, 10.0))
            .Returns(91.20m);

        var response = await _matchingService.InitiateMatchingAsync("req-1111-2222-3333", "corr-1");

        Assert.NotNull(response);
        Assert.Equal(MatchingStatus.MATCHED, response.Status);
        Assert.NotNull(response.Driver);
        Assert.Equal("Vikram", response.Driver.FirstName);
        Assert.Equal(1.50m, response.DistanceKm);
        Assert.Equal(91.20m, response.MatchScore);

        var reservation = _dbContext.DriverReservations.FirstOrDefault(r => r.DriverId == 1);
        Assert.NotNull(reservation);
        Assert.Equal(ReservationStatus.RESERVED, reservation.Status);

        _eventPublisherMock.Verify(e => e.Publish(It.IsAny<MatchingEvent>()), Times.AtLeastOnce());
    }

    [Fact]
    public async Task Match_AllDriversOffline_ReturnsNoDriversFound()
    {
        _eligibleDriver.Status = DriverStatus.OFFLINE;
        _dbContext.SaveChanges();

        var response = await _matchingService.InitiateMatchingAsync("req-1111-2222-3333", "corr-2");

        Assert.NotNull(response);
        Assert.Equal(MatchingStatus.NO_DRIVERS_FOUND, response.Status);
        Assert.Equal(0, response.CandidatesEvaluated);
    }

    [Fact]
    public async Task Match_MissingLocation_ExcludesDriver()
    {
        _locationServiceMock.Setup(l => l.GetLatestLocationAsync(1))
            .ReturnsAsync((DriverLocation?)null);

        var response = await _matchingService.InitiateMatchingAsync("req-1111-2222-3333", "corr-3");

        Assert.Equal(MatchingStatus.NO_DRIVERS_FOUND, response.Status);
    }

    [Fact]
    public async Task Match_StaleLocation_ExcludesDriver()
    {
        _locationServiceMock.Setup(l => l.GetLatestLocationAsync(1))
            .ReturnsAsync(_freshLocation);
        _locationServiceMock.Setup(l => l.IsLocationFresh(_freshLocation, 300))
            .Returns(false); // Stale!

        var response = await _matchingService.InitiateMatchingAsync("req-1111-2222-3333", "corr-4");

        Assert.Equal(MatchingStatus.NO_DRIVERS_FOUND, response.Status);
    }

    [Fact]
    public async Task Match_BeyondRadius_ExcludesDriver()
    {
        _locationServiceMock.Setup(l => l.GetLatestLocationAsync(1))
            .ReturnsAsync(_freshLocation);
        _locationServiceMock.Setup(l => l.IsLocationFresh(_freshLocation, 300))
            .Returns(true);
        _scoringPolicyServiceMock.Setup(s => s.CalculateHaversineDistanceKm(It.IsAny<double>(), It.IsAny<double>(), It.IsAny<double>(), It.IsAny<double>()))
            .Returns(15.50); // > 10 km

        var response = await _matchingService.InitiateMatchingAsync("req-1111-2222-3333", "corr-5");

        Assert.Equal(MatchingStatus.NO_DRIVERS_FOUND, response.Status);
    }

    [Fact]
    public async Task Match_IncompatibleVehicleCategory_ExcludesDriver()
    {
        _eligibleVehicle.RideTypeCategory = "PREMIER"; // Request asks for GO
        _dbContext.SaveChanges();

        var response = await _matchingService.InitiateMatchingAsync("req-1111-2222-3333", "corr-6");

        Assert.Equal(MatchingStatus.NO_DRIVERS_FOUND, response.Status);
    }

    [Fact]
    public async Task Match_ActiveReservationConflict_ExcludesDriver()
    {
        var existingReservation = new DriverReservation
        {
            ReservationUuid = "res-lock-1",
            DriverId = _eligibleDriver.Id,
            RideRequestId = 999,
            MatchingAttemptId = 999,
            Status = ReservationStatus.RESERVED,
            ExpiresAt = DateTime.UtcNow.AddSeconds(100)
        };
        _dbContext.DriverReservations.Add(existingReservation);
        _dbContext.SaveChanges();

        var response = await _matchingService.InitiateMatchingAsync("req-1111-2222-3333", "corr-7");

        Assert.Equal(MatchingStatus.NO_DRIVERS_FOUND, response.Status);
    }

    [Fact]
    public async Task Match_CancelledRideRequest_ThrowsConflict()
    {
        _validRideRequest.Status = RideRequestStatus.CANCELLED;
        _dbContext.SaveChanges();

        await Assert.ThrowsAsync<ConflictException>(() =>
            _matchingService.InitiateMatchingAsync("req-1111-2222-3333", "corr-8"));
    }

    [Fact]
    public async Task Match_RideRequestNotFound_ThrowsNotFound()
    {
        await Assert.ThrowsAsync<ResourceNotFoundException>(() =>
            _matchingService.InitiateMatchingAsync("non-existent-uuid", "corr-9"));
    }

    [Fact]
    public async Task Match_AlreadyMatched_IdempotentReturn()
    {
        var existingMatch = new MatchingAttempt
        {
            AttemptUuid = "existing-attempt-uuid",
            RideRequestId = _validRideRequest.Id,
            RideRequestUuid = _validRideRequest.Uuid,
            Status = MatchingStatus.MATCHED,
            AttemptNumber = 1,
            SelectedDriverId = _eligibleDriver.Id,
            SelectedDriver = _eligibleDriver,
            SelectedVehicleId = _eligibleVehicle.Id,
            SelectedVehicle = _eligibleVehicle,
            DistanceKm = 1.20m,
            MatchScore = 94.50m
        };
        _dbContext.MatchingAttempts.Add(existingMatch);
        _dbContext.SaveChanges();

        var response = await _matchingService.InitiateMatchingAsync("req-1111-2222-3333", "corr-10");

        Assert.NotNull(response);
        Assert.Equal("existing-attempt-uuid", response.AttemptUuid);
        Assert.Equal(MatchingStatus.MATCHED, response.Status);
    }

    [Fact]
    public async Task Retry_PreviousNoDriversFound_SucceedsWithIncrementedAttemptNumber()
    {
        var prevFailed = new MatchingAttempt
        {
            AttemptUuid = "prev-failed-uuid",
            RideRequestId = _validRideRequest.Id,
            RideRequestUuid = _validRideRequest.Uuid,
            Status = MatchingStatus.NO_DRIVERS_FOUND,
            AttemptNumber = 1
        };
        _dbContext.MatchingAttempts.Add(prevFailed);
        _dbContext.SaveChanges();

        _locationServiceMock.Setup(l => l.GetLatestLocationAsync(1))
            .ReturnsAsync(_freshLocation);
        _locationServiceMock.Setup(l => l.IsLocationFresh(_freshLocation, 300))
            .Returns(true);
        _scoringPolicyServiceMock.Setup(s => s.CalculateHaversineDistanceKm(It.IsAny<double>(), It.IsAny<double>(), It.IsAny<double>(), It.IsAny<double>()))
            .Returns(1.50);
        _scoringPolicyServiceMock.Setup(s => s.CalculateMatchScore(_eligibleDriver, 1.50, 10.0))
            .Returns(91.20m);

        var retryRequest = new MatchingRetryRequestDto { Reason = "Retry after delay" };
        var response = await _matchingService.RetryMatchingAsync("req-1111-2222-3333", retryRequest);

        Assert.NotNull(response);
        Assert.Equal(2, response.AttemptNumber);
        Assert.Equal(MatchingStatus.MATCHED, response.Status);
    }
}
