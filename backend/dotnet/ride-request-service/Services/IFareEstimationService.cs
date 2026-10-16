using RideRequestService.DTOs.Response;
using RideRequestService.Entities;

namespace RideRequestService.Services;

public interface IFareEstimationService
{
    decimal CalculateDistanceKm(decimal? pickupLat, decimal? pickupLng, decimal? dropLat, decimal? dropLng);
    int CalculateDurationMinutes(decimal distanceKm);
    FareEstimateResponse EstimateFare(decimal distanceKm, int durationMinutes, RideType rideType);
    FareEstimateResponse EstimateFare(decimal? pickupLat, decimal? pickupLng, decimal? dropLat, decimal? dropLng, RideType rideType);
}
