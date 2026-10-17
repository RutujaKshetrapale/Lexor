using Xunit;
using DriverMatchingService.Entities;
using DriverMatchingService.Services;

namespace DriverMatchingService.Tests;

public class ScoringPolicyServiceTests
{
    private readonly IScoringPolicyService _scoringPolicyService = new ScoringPolicyService();

    [Fact]
    public void CalculateHaversineDistance_ReturnsAccurateDistance()
    {
        // MG Road to Prestige Tech Park Marathahalli (~7-12 km)
        double lat1 = 12.9784;
        double lon1 = 77.6408;
        double lat2 = 12.9372;
        double lon2 = 77.6949;

        double distance = _scoringPolicyService.CalculateHaversineDistanceKm(lat1, lon1, lat2, lon2);

        Assert.True(distance > 7.0 && distance < 12.0, $"Distance should be ~7-12 km, got: {distance}");
    }

    [Fact]
    public void CalculateHaversineDistance_SameCoordinates_ReturnsZero()
    {
        double lat = 12.9784;
        double lon = 77.6408;

        double distance = _scoringPolicyService.CalculateHaversineDistanceKm(lat, lon, lat, lon);

        Assert.Equal(0.0, distance);
    }

    [Fact]
    public void CalculateMatchScore_DeterministicScoreCalculation()
    {
        var driver = new DriverProfile
        {
            Rating = 4.90m,
            AcceptanceRate = 98.50m
        };

        // 2 km out of 10 km max radius
        decimal score = _scoringPolicyService.CalculateMatchScore(driver, 2.0, 10.0);

        Assert.True(score > 88.0m && score < 90.0m, $"Score should be ~89.10, got: {score}");
    }

    [Fact]
    public void CloserDriver_ReceivesHigherScore_ThanFartherDriver()
    {
        var driver = new DriverProfile
        {
            Rating = 4.80m,
            AcceptanceRate = 95.00m
        };

        decimal closeScore = _scoringPolicyService.CalculateMatchScore(driver, 1.0, 10.0);
        decimal farScore = _scoringPolicyService.CalculateMatchScore(driver, 8.0, 10.0);

        Assert.True(closeScore > farScore, "Closer driver should have higher score");
    }
}
