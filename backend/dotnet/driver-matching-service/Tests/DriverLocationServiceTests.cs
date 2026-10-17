using Microsoft.Extensions.Logging;
using Moq;
using Xunit;
using DriverMatchingService.Data;
using DriverMatchingService.DTOs.Request;
using DriverMatchingService.Entities;
using DriverMatchingService.Enums;
using DriverMatchingService.Exceptions;
using DriverMatchingService.Services;

namespace DriverMatchingService.Tests;

public class DriverLocationServiceTests
{
    private readonly LexorDbContext _dbContext;
    private readonly IDriverLocationService _locationService;

    public DriverLocationServiceTests()
    {
        _dbContext = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var loggerMock = new Mock<ILogger<DriverLocationService>>();
        _locationService = new DriverLocationService(_dbContext, loggerMock.Object);

        // Seed sample driver
        var user = new User
        {
            Id = 3,
            Uuid = "driver-uuid-123",
            FirstName = "Vikram",
            LastName = "Singh",
            Email = "vikram.singh@example.com",
            PhoneNumber = "+919876543212",
            PasswordHash = "hash",
            Role = Role.DRIVER,
            Status = UserStatus.ACTIVE
        };

        var profile = new DriverProfile
        {
            Id = 1,
            UserId = user.Id,
            User = user,
            Status = DriverStatus.ONLINE,
            IsVerified = true,
            OnboardingStatus = OnboardingStatus.VERIFIED,
            BackgroundCheckStatus = BackgroundCheckStatus.PASSED
        };

        _dbContext.Users.Add(user);
        _dbContext.DriverProfiles.Add(profile);
        _dbContext.SaveChanges();
    }

    [Fact]
    public async Task UpdateDriverLocation_ValidCoordinates_Succeeds()
    {
        var request = new LocationUpdateRequestDto
        {
            Latitude = 12.97159870m,
            Longitude = 77.59456270m,
            Heading = 145.0m,
            SpeedKmh = 40.0m,
            Timestamp = DateTime.UtcNow
        };

        var response = await _locationService.UpdateDriverLocationAsync("driver-uuid-123", request);

        Assert.NotNull(response);
        Assert.Equal("driver-uuid-123", response.DriverUuid);
        Assert.Equal("SUCCESS", response.Status);
        Assert.Equal(12.97159870m, response.Latitude);
        Assert.Equal(77.59456270m, response.Longitude);
    }

    [Fact]
    public async Task UpdateDriverLocation_InvalidLatitude_ThrowsBadRequest()
    {
        var request = new LocationUpdateRequestDto
        {
            Latitude = 95.0m, // Invalid!
            Longitude = 77.59456270m
        };

        await Assert.ThrowsAsync<BadRequestException>(() =>
            _locationService.UpdateDriverLocationAsync("driver-uuid-123", request));
    }

    [Fact]
    public async Task UpdateDriverLocation_InvalidLongitude_ThrowsBadRequest()
    {
        var request = new LocationUpdateRequestDto
        {
            Latitude = 12.97159870m,
            Longitude = -195.0m // Invalid!
        };

        await Assert.ThrowsAsync<BadRequestException>(() =>
            _locationService.UpdateDriverLocationAsync("driver-uuid-123", request));
    }

    [Fact]
    public async Task UpdateDriverLocation_FutureTimestamp_ThrowsBadRequest()
    {
        var request = new LocationUpdateRequestDto
        {
            Latitude = 12.97159870m,
            Longitude = 77.59456270m,
            Timestamp = DateTime.UtcNow.AddMinutes(5) // Future!
        };

        await Assert.ThrowsAsync<BadRequestException>(() =>
            _locationService.UpdateDriverLocationAsync("driver-uuid-123", request));
    }

    [Fact]
    public async Task UpdateDriverLocation_DriverNotFound_ThrowsResourceNotFound()
    {
        var request = new LocationUpdateRequestDto
        {
            Latitude = 12.97159870m,
            Longitude = 77.59456270m
        };

        await Assert.ThrowsAsync<ResourceNotFoundException>(() =>
            _locationService.UpdateDriverLocationAsync("non-existent-uuid", request));
    }

    [Fact]
    public void IsLocationFresh_ChecksThresholdCorrectly()
    {
        var freshLoc = new DriverLocation
        {
            RecordedAt = DateTime.UtcNow.AddSeconds(-60)
        };

        var staleLoc = new DriverLocation
        {
            RecordedAt = DateTime.UtcNow.AddSeconds(-600)
        };

        Assert.True(_locationService.IsLocationFresh(freshLoc, 300));
        Assert.False(_locationService.IsLocationFresh(staleLoc, 300));
        Assert.False(_locationService.IsLocationFresh(null, 300));
    }
}
