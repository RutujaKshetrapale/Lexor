using RideRequestService.Entities;
using RideRequestService.Enums;
using RideRequestService.Exceptions;
using RideRequestService.Services;
using Xunit;

namespace RideRequestService.Tests;

public class AdminRideRequestServiceTests
{
    private static (User User, RideType RideType, RiderProfile RiderProfile) SeedDatabase(Data.LexorDbContext context)
    {
        var user = new User
        {
            Id = 1,
            Uuid = "11111111-1111-1111-8111-111111111111",
            Email = "rider1@example.com",
            FirstName = "Rajesh",
            LastName = "Kumar",
            Role = Role.RIDER,
            Status = UserStatus.ACTIVE
        };
        context.Users.Add(user);

        var riderProfile = new RiderProfile
        {
            Id = 1,
            UserId = user.Id,
            User = user,
            Rating = 5.0m
        };
        context.RiderProfiles.Add(riderProfile);

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
        return (user, rideType, riderProfile);
    }

    [Fact]
    public async Task GetAllRideRequests_ReturnsPaginatedList()
    {
        var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var (user, rideType, riderProfile) = SeedDatabase(db);

        for (int i = 1; i <= 5; i++)
        {
            db.RideRequests.Add(new RideRequest
            {
                Id = i,
                Uuid = $"req-{i}",
                RiderId = riderProfile.Id,
                Rider = riderProfile,
                RideTypeId = rideType.Id,
                RideType = rideType,
                PickupAddress = $"Pickup {i}",
                DropAddress = $"Drop {i}",
                Status = RideRequestStatus.REQUESTED,
                RequestedAt = DateTime.UtcNow.AddMinutes(-i)
            });
        }
        db.SaveChanges();

        var service = new AdminRideRequestService(db);

        var pageResult = await service.GetAllRideRequestsAsync(0, 3, null, null);

        Assert.Equal(3, pageResult.Content.Count);
        Assert.Equal(5, pageResult.TotalElements);
        Assert.Equal(2, pageResult.TotalPages);
        Assert.True(pageResult.First);
        Assert.False(pageResult.Last);
    }

    [Fact]
    public async Task GetAllRideRequests_FilterByStatus_ReturnsOnlyMatching()
    {
        var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var (user, rideType, riderProfile) = SeedDatabase(db);

        db.RideRequests.AddRange(
            new RideRequest { Id = 1, Uuid = "r1", RiderId = riderProfile.Id, Rider = riderProfile, RideTypeId = rideType.Id, RideType = rideType, PickupAddress = "A", DropAddress = "B", Status = RideRequestStatus.REQUESTED },
            new RideRequest { Id = 2, Uuid = "r2", RiderId = riderProfile.Id, Rider = riderProfile, RideTypeId = rideType.Id, RideType = rideType, PickupAddress = "C", DropAddress = "D", Status = RideRequestStatus.CANCELLED }
        );
        db.SaveChanges();

        var service = new AdminRideRequestService(db);

        var result = await service.GetAllRideRequestsAsync(0, 10, RideRequestStatus.CANCELLED, null);

        Assert.Single(result.Content);
        Assert.Equal("r2", result.Content[0].RequestUuid);
        Assert.Equal(RideRequestStatus.CANCELLED, result.Content[0].Status);
    }

    [Fact]
    public async Task GetRideRequestByUuid_Found_ReturnsDetails()
    {
        var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var (user, rideType, riderProfile) = SeedDatabase(db);

        db.RideRequests.Add(new RideRequest
        {
            Id = 1,
            Uuid = "target-req-uuid",
            RiderId = riderProfile.Id,
            Rider = riderProfile,
            RideTypeId = rideType.Id,
            RideType = rideType,
            PickupAddress = "MG Road",
            DropAddress = "Indiranagar",
            Status = RideRequestStatus.REQUESTED
        });
        db.SaveChanges();

        var service = new AdminRideRequestService(db);

        var response = await service.GetRideRequestByUuidAsync("target-req-uuid");

        Assert.NotNull(response);
        Assert.Equal("target-req-uuid", response.RequestUuid);
        Assert.Equal("MG Road", response.PickupAddress);
    }

    [Fact]
    public async Task GetRideRequestByUuid_NotFound_ThrowsResourceNotFoundException()
    {
        var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var service = new AdminRideRequestService(db);

        await Assert.ThrowsAsync<ResourceNotFoundException>(() =>
            service.GetRideRequestByUuidAsync("non-existent-uuid"));
    }
}
