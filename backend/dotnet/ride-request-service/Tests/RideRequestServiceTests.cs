using System.Security.Claims;
using Microsoft.Extensions.Logging;
using Moq;
using RideRequestService.DTOs.Request;
using RideRequestService.DTOs.Response;
using RideRequestService.Entities;
using RideRequestService.Enums;
using RideRequestService.Exceptions;
using RideRequestService.Services;
using Xunit;

namespace RideRequestService.Tests;

public class RideRequestServiceTests
{
    private static ClaimsPrincipal CreatePrincipal(string email, string uuid, long userId, string role)
    {
        var identity = new ClaimsIdentity(new[]
        {
            new Claim("email", email),
            new Claim(ClaimTypes.Email, email),
            new Claim("uuid", uuid),
            new Claim(ClaimTypes.NameIdentifier, uuid),
            new Claim("userId", userId.ToString()),
            new Claim("role", role),
            new Claim(ClaimTypes.Role, role)
        }, "TestAuth");

        return new ClaimsPrincipal(identity);
    }

    private static (User User, RideType RideType) SeedDatabase(Data.LexorDbContext context)
    {
        var user = new User
        {
            Id = 1,
            Uuid = "11111111-1111-1111-8111-111111111111",
            Email = "rider@example.com",
            FirstName = "Rajesh",
            LastName = "Kumar",
            Role = Role.RIDER,
            Status = UserStatus.ACTIVE
        };
        context.Users.Add(user);

        var rideType = new RideType
        {
            Id = 1,
            Name = "LEXOR Go",
            Code = "GO",
            BaseFare = 50.00m,
            PerKmRate = 12.00m,
            PerMinuteRate = 2.00m,
            MinimumFare = 80.00m,
            IsActive = true
        };
        context.RideTypes.Add(rideType);

        context.SaveChanges();
        return (user, rideType);
    }

    [Fact]
    public async Task CreateRideRequest_NonRiderRole_ThrowsForbiddenException()
    {
        var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var fareMock = new Mock<IFareEstimationService>();
        var clientMock = new Mock<IRideTypeClient>();
        var loggerMock = new Mock<ILogger<RideRequestService.Services.RideRequestService>>();

        var service = new RideRequestService.Services.RideRequestService(db, fareMock.Object, clientMock.Object, loggerMock.Object);
        var driverPrincipal = CreatePrincipal("driver@example.com", "d-uuid", 2, "DRIVER");

        var request = new CreateRideRequestDto
        {
            PickupAddress = "MG Road",
            PickupLatitude = 12.9784m,
            PickupLongitude = 77.6408m,
            DropAddress = "Prestige Tech Park",
            DropLatitude = 12.9372m,
            DropLongitude = 77.6949m,
            RideTypeCode = "GO"
        };

        var ex = await Assert.ThrowsAsync<ForbiddenException>(() =>
            service.CreateRideRequestAsync(driverPrincipal, request, null));

        Assert.Contains("Only authenticated users with role RIDER", ex.Message);
    }

    [Fact]
    public async Task CreateRideRequest_ValidRequestWithCode_CreatesSuccessfully()
    {
        var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var (user, rideType) = SeedDatabase(db);

        var fareMock = new Mock<IFareEstimationService>();
        fareMock.Setup(f => f.EstimateFare(It.IsAny<decimal?>(), It.IsAny<decimal?>(), It.IsAny<decimal?>(), It.IsAny<decimal?>(), It.IsAny<RideType>()))
            .Returns(new FareEstimateResponse
            {
                DistanceKm = 10.0m,
                DurationMinutes = 25,
                BaseFare = 50.0m,
                DistanceFare = 120.0m,
                TimeFare = 50.0m,
                SubtotalFare = 220.0m,
                MinimumFare = 80.0m,
                FinalEstimatedFare = 220.0m,
                Currency = "INR"
            });

        var clientMock = new Mock<IRideTypeClient>();
        var loggerMock = new Mock<ILogger<RideRequestService.Services.RideRequestService>>();

        var service = new RideRequestService.Services.RideRequestService(db, fareMock.Object, clientMock.Object, loggerMock.Object);
        var riderPrincipal = CreatePrincipal(user.Email, user.Uuid, user.Id, "RIDER");

        var request = new CreateRideRequestDto
        {
            PickupAddress = "MG Road, Indiranagar",
            PickupLatitude = 12.9784m,
            PickupLongitude = 77.6408m,
            DropAddress = "Prestige Tech Park",
            DropLatitude = 12.9372m,
            DropLongitude = 77.6949m,
            RideTypeCode = "GO"
        };

        var response = await service.CreateRideRequestAsync(riderPrincipal, request, null);

        Assert.NotNull(response);
        Assert.NotEmpty(response.RequestUuid);
        Assert.Equal("GO", response.RideTypeCode);
        Assert.Equal(220.0m, response.EstimatedFare);
        Assert.Equal(RideRequestStatus.REQUESTED, response.Status);
        Assert.Equal("Rajesh Kumar", response.RiderName);
    }

    [Fact]
    public async Task CreateRideRequest_DisabledRideType_ThrowsBadRequestException()
    {
        var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var (user, rideType) = SeedDatabase(db);
        rideType.IsActive = false;
        db.SaveChanges();

        var fareMock = new Mock<IFareEstimationService>();
        var clientMock = new Mock<IRideTypeClient>();
        var loggerMock = new Mock<ILogger<RideRequestService.Services.RideRequestService>>();

        var service = new RideRequestService.Services.RideRequestService(db, fareMock.Object, clientMock.Object, loggerMock.Object);
        var riderPrincipal = CreatePrincipal(user.Email, user.Uuid, user.Id, "RIDER");

        var request = new CreateRideRequestDto
        {
            PickupAddress = "MG Road",
            PickupLatitude = 12.9784m,
            PickupLongitude = 77.6408m,
            DropAddress = "Prestige Tech Park",
            DropLatitude = 12.9372m,
            DropLongitude = 77.6949m,
            RideTypeCode = "GO"
        };

        var ex = await Assert.ThrowsAsync<BadRequestException>(() =>
            service.CreateRideRequestAsync(riderPrincipal, request, null));

        Assert.Contains("is currently disabled", ex.Message);
    }

    [Fact]
    public async Task GetMyRideRequestByUuid_OwnershipEnforced_ThrowsForbiddenForDifferentRider()
    {
        var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var (user1, rideType) = SeedDatabase(db);

        var user2 = new User
        {
            Id = 2,
            Uuid = "22222222-2222-2222-8222-222222222222",
            Email = "other@example.com",
            FirstName = "Ananya",
            LastName = "Iyer",
            Role = Role.RIDER,
            Status = UserStatus.ACTIVE
        };
        db.Users.Add(user2);

        var riderProfile1 = new RiderProfile { Id = 1, UserId = user1.Id, User = user1, Rating = 5.0m };
        var riderProfile2 = new RiderProfile { Id = 2, UserId = user2.Id, User = user2, Rating = 5.0m };
        db.RiderProfiles.AddRange(riderProfile1, riderProfile2);

        var rideRequest = new RideRequest
        {
            Id = 1,
            Uuid = "req-111",
            RiderId = riderProfile1.Id,
            Rider = riderProfile1,
            RideTypeId = rideType.Id,
            RideType = rideType,
            PickupAddress = "A",
            DropAddress = "B",
            Status = RideRequestStatus.REQUESTED
        };
        db.RideRequests.Add(rideRequest);
        db.SaveChanges();

        var service = new RideRequestService.Services.RideRequestService(db, Mock.Of<IFareEstimationService>(), Mock.Of<IRideTypeClient>(), Mock.Of<ILogger<RideRequestService.Services.RideRequestService>>());

        // Rider 2 tries to access Rider 1's request
        var rider2Principal = CreatePrincipal(user2.Email, user2.Uuid, user2.Id, "RIDER");

        var ex = await Assert.ThrowsAsync<ForbiddenException>(() =>
            service.GetMyRideRequestByUuidAsync(rider2Principal, "req-111"));

        Assert.Contains("do not have permission", ex.Message);
    }

    [Fact]
    public async Task CancelRideRequest_InRequestedStatus_CancelsSuccessfully()
    {
        var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var (user, rideType) = SeedDatabase(db);

        var riderProfile = new RiderProfile { Id = 1, UserId = user.Id, User = user, Rating = 5.0m };
        db.RiderProfiles.Add(riderProfile);

        var rideRequest = new RideRequest
        {
            Id = 1,
            Uuid = "req-can-1",
            RiderId = riderProfile.Id,
            Rider = riderProfile,
            RideTypeId = rideType.Id,
            RideType = rideType,
            PickupAddress = "A",
            DropAddress = "B",
            Status = RideRequestStatus.REQUESTED
        };
        db.RideRequests.Add(rideRequest);
        db.SaveChanges();

        var service = new RideRequestService.Services.RideRequestService(db, Mock.Of<IFareEstimationService>(), Mock.Of<IRideTypeClient>(), Mock.Of<ILogger<RideRequestService.Services.RideRequestService>>());
        var riderPrincipal = CreatePrincipal(user.Email, user.Uuid, user.Id, "RIDER");

        var response = await service.CancelRideRequestAsync(riderPrincipal, "req-can-1", new CancelRideRequestDto { Reason = "Plan changed" });

        Assert.Equal(RideRequestStatus.CANCELLED, response.Status);
    }

    [Fact]
    public async Task CancelRideRequest_AlreadyMatched_ThrowsConflictException()
    {
        var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var (user, rideType) = SeedDatabase(db);

        var riderProfile = new RiderProfile { Id = 1, UserId = user.Id, User = user, Rating = 5.0m };
        db.RiderProfiles.Add(riderProfile);

        var rideRequest = new RideRequest
        {
            Id = 1,
            Uuid = "req-matched-1",
            RiderId = riderProfile.Id,
            Rider = riderProfile,
            RideTypeId = rideType.Id,
            RideType = rideType,
            PickupAddress = "A",
            DropAddress = "B",
            Status = RideRequestStatus.MATCHED
        };
        db.RideRequests.Add(rideRequest);
        db.SaveChanges();

        var service = new RideRequestService.Services.RideRequestService(db, Mock.Of<IFareEstimationService>(), Mock.Of<IRideTypeClient>(), Mock.Of<ILogger<RideRequestService.Services.RideRequestService>>());
        var riderPrincipal = CreatePrincipal(user.Email, user.Uuid, user.Id, "RIDER");

        var ex = await Assert.ThrowsAsync<ConflictException>(() =>
            service.CancelRideRequestAsync(riderPrincipal, "req-matched-1", null));

        Assert.Contains("matched with a driver", ex.Message);
    }

    [Fact]
    public async Task CancelRideRequest_AlreadyCancelled_ThrowsConflictException()
    {
        var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var (user, rideType) = SeedDatabase(db);

        var riderProfile = new RiderProfile { Id = 1, UserId = user.Id, User = user, Rating = 5.0m };
        db.RiderProfiles.Add(riderProfile);

        var rideRequest = new RideRequest
        {
            Id = 1,
            Uuid = "req-cancel-dup",
            RiderId = riderProfile.Id,
            Rider = riderProfile,
            RideTypeId = rideType.Id,
            RideType = rideType,
            PickupAddress = "A",
            DropAddress = "B",
            Status = RideRequestStatus.CANCELLED
        };
        db.RideRequests.Add(rideRequest);
        db.SaveChanges();

        var service = new RideRequestService.Services.RideRequestService(db, Mock.Of<IFareEstimationService>(), Mock.Of<IRideTypeClient>(), Mock.Of<ILogger<RideRequestService.Services.RideRequestService>>());
        var riderPrincipal = CreatePrincipal(user.Email, user.Uuid, user.Id, "RIDER");

        var ex = await Assert.ThrowsAsync<ConflictException>(() =>
            service.CancelRideRequestAsync(riderPrincipal, "req-cancel-dup", null));

        Assert.Contains("already cancelled", ex.Message);
    }
}
