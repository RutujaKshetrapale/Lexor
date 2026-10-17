using DriverMatchingService.Entities;

namespace DriverMatchingService.Services;

public class ScoringPolicyService : IScoringPolicyService
{
    private const double EarthRadiusKm = 6371.0;

    public double CalculateHaversineDistanceKm(double lat1, double lon1, double lat2, double lon2)
    {
        double dLat = ToRadians(lat2 - lat1);
        double dLon = ToRadians(lon2 - lon1);

        double a = Math.Sin(dLat / 2) * Math.Sin(dLat / 2)
                 + Math.Cos(ToRadians(lat1)) * Math.Cos(ToRadians(lat2))
                 * Math.Sin(dLon / 2) * Math.Sin(dLon / 2);

        double c = 2 * Math.Atan2(Math.Sqrt(a), Math.Sqrt(1 - a));

        double distance = EarthRadiusKm * c;
        return Math.Round(distance, 2);
    }

    public decimal CalculateMatchScore(DriverProfile driver, double distanceKm, double maxRadiusKm)
    {
        // 1. Distance factor: Up to 50 points
        double distanceFraction = maxRadiusKm > 0 ? (distanceKm / maxRadiusKm) : 1.0;
        double distanceScore = Math.Max(0.0, 50.0 * (1.0 - distanceFraction));

        // 2. Rating factor: Up to 30 points
        double ratingVal = (double)driver.Rating;
        double ratingScore = Math.Min(30.0, (ratingVal / 5.0) * 30.0);

        // 3. Acceptance rate factor: Up to 20 points
        double acceptanceVal = (double)driver.AcceptanceRate;
        double acceptanceScore = Math.Min(20.0, (acceptanceVal / 100.0) * 20.0);

        double totalScore = distanceScore + ratingScore + acceptanceScore;
        return (decimal)Math.Round(totalScore, 2);
    }

    private static double ToRadians(double degrees) => degrees * (Math.PI / 180.0);
}
