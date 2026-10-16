using RideRequestService.DTOs.Response;
using RideRequestService.Enums;

namespace RideRequestService.Services;

public interface IAdminRideRequestService
{
    Task<PageResponse<RideRequestResponse>> GetAllRideRequestsAsync(int page, int size, RideRequestStatus? status, long? riderId);
    Task<RideRequestResponse> GetRideRequestByUuidAsync(string requestUuid);
}
