using Microsoft.Extensions.Logging;
using Moq;
using RideRequestService.Entities;
using RideRequestService.Exceptions;
using RideRequestService.Services;
using Xunit;

namespace RideRequestService.Tests;

public class FareEstimationServiceTests
{
    private readonly FareEstimationService _fareService;

    public FareEstimationServiceTests()
    {
        var loggerMock = new Mock<ILogger<FareEstimationService>>();
        _fareService = new FareEstimationService(loggerMock.Object);
    }

    [Fact]
    public void CalculateDistanceKm_ValidCoordinates_ReturnsAugmentedDistance()
    {
        // MG Road (12.9784, 77.6408) to Prestige Tech Park (12.9372, 77.6949)
        var pickupLat = 12.97840000m;
        var pickupLng = 77.64080000m;
        var dropLat = 12.93720000m;
        var dropLng = 77.69490000m;

        var distance = _fareService.CalculateDistanceKm(pickupLat, pickupLng, dropLat, dropLng);

        // Straight line is ~7.4 km, with 1.30 detour index it should be ~9.6-9.7 km
        Assert.True(distance > 7.0m && distance < 12.0m, $"Distance {distance} km is out of expected range");
    }

    [Fact]
    public void CalculateDistanceKm_NullCoordinates_ThrowsBadRequestException()
    {
        var ex = Assert.Throws<BadRequestException>(() =>
            _fareService.CalculateDistanceKm(null, 77.6408m, 12.9372m, 77.6949m));

        Assert.Contains("All coordinate parameters", ex.Message);
    }

    [Fact]
    public void CalculateDistanceKm_IdenticalCoordinates_ThrowsBadRequestException()
    {
        var lat = 12.97840000m;
        var lng = 77.64080000m;

        var ex = Assert.Throws<BadRequestException>(() =>
            _fareService.CalculateDistanceKm(lat, lng, lat, lng));

        Assert.Contains("identical or excessively close", ex.Message);
    }

    [Fact]
    public void CalculateDurationMinutes_StandardDistance_ComputesAt25Kmh()
    {
        // 12.5 km at 25 km/h = 0.5 hours = 30 minutes
        var distance = 12.5m;
        var duration = _fareService.CalculateDurationMinutes(distance);

        Assert.Equal(30, duration);
    }

    [Fact]
    public void CalculateDurationMinutes_VeryShortDistance_EnforcesMinimum5Minutes()
    {
        var distance = 0.5m;
        var duration = _fareService.CalculateDurationMinutes(distance);

        Assert.Equal(5, duration);
    }

    [Fact]
    public void EstimateFare_StandardCategory_CalculatesCorrectBreakdown()
    {
        var rideType = new RideType
        {
            Id = 1,
            Name = "LEXOR Go",
            Code = "GO",
            BaseFare = 50.00m,
            PerKmRate = 12.00m,
            PerMinuteRate = 2.00m,
            MinimumFare = 80.00m
        };

        var distanceKm = 10.00m;
        var durationMinutes = 20;

        var result = _fareService.EstimateFare(distanceKm, durationMinutes, rideType);

        Assert.NotNull(result);
        Assert.Equal(50.00m, result.BaseFare);
        Assert.Equal(120.00m, result.DistanceFare); // 10 * 12
        Assert.Equal(40.00m, result.TimeFare);     // 20 * 2
        Assert.Equal(210.00m, result.SubtotalFare); // 50 + 120 + 40
        Assert.Equal(80.00m, result.MinimumFare);
        Assert.Equal(210.00m, result.FinalEstimatedFare);
        Assert.Equal("INR", result.Currency);
    }

    [Fact]
    public void EstimateFare_ShortDistance_AppliesMinimumFare()
    {
        var rideType = new RideType
        {
            Id = 1,
            Name = "LEXOR Go",
            Code = "GO",
            BaseFare = 30.00m,
            PerKmRate = 10.00m,
            PerMinuteRate = 1.50m,
            MinimumFare = 100.00m
        };

        var distanceKm = 1.00m;
        var durationMinutes = 5;

        // Subtotal = 30 + 10 + 7.5 = 47.50, which is < minimumFare (100)
        var result = _fareService.EstimateFare(distanceKm, durationMinutes, rideType);

        Assert.Equal(47.50m, result.SubtotalFare);
        Assert.Equal(100.00m, result.FinalEstimatedFare);
    }

    [Fact]
    public void EstimateFare_NullRideType_ThrowsBadRequestException()
    {
        Assert.Throws<BadRequestException>(() =>
            _fareService.EstimateFare(5.0m, 15, null!));
    }
}
