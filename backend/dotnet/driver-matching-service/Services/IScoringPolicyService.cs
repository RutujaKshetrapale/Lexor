using DriverMatchingService.Entities;

namespace DriverMatchingService.Services;

public interface IScoringPolicyService
{
    double CalculateHaversineDistanceKm(double lat1, double lon1, double lat2, double lon2);

    decimal CalculateMatchScore(DriverProfile driver, double distanceKm, double maxRadiusKm);
}
