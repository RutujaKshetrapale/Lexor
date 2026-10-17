using DriverMatchingService.DTOs.Request;
using DriverMatchingService.DTOs.Response;
using DriverMatchingService.Entities;

namespace DriverMatchingService.Services;

public interface IDriverLocationService
{
    Task<LocationUpdateResponse> UpdateDriverLocationAsync(string driverUuid, LocationUpdateRequestDto request);

    Task<DriverLocation?> GetLatestLocationAsync(long driverId);

    bool IsLocationFresh(DriverLocation? location, long maxAgeSeconds);
}
