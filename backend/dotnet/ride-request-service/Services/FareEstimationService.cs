using RideRequestService.DTOs.Response;
using RideRequestService.Entities;
using RideRequestService.Exceptions;

namespace RideRequestService.Services;

/// <summary>
/// Dedicated fare estimation and route approximation service.
/// Uses the great-circle Haversine formula augmented by an empirical urban street-network
/// tortuosity factor (1.30x) and average urban speed (25 km/h).
/// </summary>
public class FareEstimationService : IFareEstimationService
{
    private readonly ILogger<FareEstimationService> _logger;

    private const double EarthRadiusKm = 6371.0;
    private const double UrbanRoutingFactor = 1.30;
    private const double AverageUrbanSpeedKmh = 25.0;
    private const int MinimumDurationMinutes = 5;
    private const double MinimumDistanceKm = 0.05; // 50 meters

    public FareEstimationService(ILogger<FareEstimationService> logger)
    {
        _logger = logger;
    }

    public decimal CalculateDistanceKm(decimal? pickupLat, decimal? pickupLng, decimal? dropLat, decimal? dropLng)
    {
        if (!pickupLat.HasValue || !pickupLng.HasValue || !dropLat.HasValue || !dropLng.HasValue)
        {
            throw new BadRequestException("All coordinate parameters (latitude and longitude) must be provided");
        }

        double lat1 = (double)pickupLat.Value;
        double lon1 = (double)pickupLng.Value;
        double lat2 = (double)dropLat.Value;
        double lon2 = (double)dropLng.Value;

        // Haversine great-circle distance
        double dLat = ToRadians(lat2 - lat1);
        double dLon = ToRadians(lon2 - lon1);

        double a = Math.Sin(dLat / 2.0) * Math.Sin(dLat / 2.0)
                 + Math.Cos(ToRadians(lat1)) * Math.Cos(ToRadians(lat2))
                 * Math.Sin(dLon / 2.0) * Math.Sin(dLon / 2.0);

        double c = 2.0 * Math.Atan2(Math.Sqrt(a), Math.Sqrt(1.0 - a));
        double straightLineDistance = EarthRadiusKm * c;

        if (straightLineDistance < MinimumDistanceKm)
        {
            throw new BadRequestException("Pickup and destination locations cannot be identical or excessively close (minimum 50 meters required)");
        }

        double estimatedRoadDistance = straightLineDistance * UrbanRoutingFactor;
        return Math.Round((decimal)estimatedRoadDistance, 2, MidpointRounding.AwayFromZero);
    }

    public int CalculateDurationMinutes(decimal distanceKm)
    {
        if (distanceKm <= 0)
        {
            return MinimumDurationMinutes;
        }

        double distance = (double)distanceKm;
        double hours = distance / AverageUrbanSpeedKmh;
        int minutes = (int)Math.Round(hours * 60.0, MidpointRounding.AwayFromZero);

        return Math.Max(minutes, MinimumDurationMinutes);
    }

    public FareEstimateResponse EstimateFare(decimal distanceKm, int durationMinutes, RideType rideType)
    {
        if (rideType == null)
        {
            throw new BadRequestException("Ride category must be provided for fare calculation");
        }

        decimal baseFare = rideType.BaseFare;
        decimal perKmRate = rideType.PerKmRate;
        decimal perMinuteRate = rideType.PerMinuteRate;
        decimal minimumFare = rideType.MinimumFare;

        decimal distanceFare = Math.Round(distanceKm * perKmRate, 2, MidpointRounding.AwayFromZero);
        decimal timeFare = Math.Round(durationMinutes * perMinuteRate, 2, MidpointRounding.AwayFromZero);

        decimal subtotal = Math.Round(baseFare + distanceFare + timeFare, 2, MidpointRounding.AwayFromZero);
        decimal finalFare = Math.Max(subtotal, minimumFare);

        _logger.LogDebug("Estimated fare for category '{Name}' ({Distance} km, {Duration} mins): subtotal={Subtotal}, minFare={MinFare}, finalFare={FinalFare}",
            rideType.Name, distanceKm, durationMinutes, subtotal, minimumFare, finalFare);

        return new FareEstimateResponse
        {
            DistanceKm = distanceKm,
            DurationMinutes = durationMinutes,
            BaseFare = baseFare,
            DistanceFare = distanceFare,
            TimeFare = timeFare,
            SubtotalFare = subtotal,
            MinimumFare = minimumFare,
            FinalEstimatedFare = finalFare,
            Currency = "INR"
        };
    }

    public FareEstimateResponse EstimateFare(decimal? pickupLat, decimal? pickupLng, decimal? dropLat, decimal? dropLng, RideType rideType)
    {
        var distanceKm = CalculateDistanceKm(pickupLat, pickupLng, dropLat, dropLng);
        var durationMinutes = CalculateDurationMinutes(distanceKm);
        return EstimateFare(distanceKm, durationMinutes, rideType);
    }

    private static double ToRadians(double angle) => (Math.PI / 180.0) * angle;
}
