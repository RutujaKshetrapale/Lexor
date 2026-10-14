using RideTypeService.DTOs.Request;
using RideTypeService.DTOs.Response;

namespace RideTypeService.Services;

public interface IRideTypeService
{
    Task<List<RideTypeResponse>> GetEnabledRideTypesAsync();

    Task<RideTypeResponse> GetEnabledRideTypeByUuidAsync(string uuid);

    Task<List<RideTypeResponse>> GetAllRideTypesAdminAsync();

    Task<RideTypeResponse> GetRideTypeByUuidAdminAsync(string uuid);

    Task<RideTypeResponse> CreateRideTypeAsync(CreateRideTypeRequest request);

    Task<RideTypeResponse> UpdateRideTypeAsync(string uuid, UpdateRideTypeRequest request);

    Task<RideTypeResponse> UpdateRideTypeStatusAsync(string uuid, UpdateRideTypeStatusRequest request);

    Task DeleteRideTypeSafelyAsync(string uuid);
}
