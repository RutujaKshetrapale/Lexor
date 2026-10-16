using System.Security.Claims;
using RideRequestService.DTOs.Request;
using RideRequestService.DTOs.Response;
using RideRequestService.Enums;

namespace RideRequestService.Services;

public interface IRideRequestService
{
    Task<RideRequestResponse> CreateRideRequestAsync(ClaimsPrincipal riderPrincipal, CreateRideRequestDto request, string? bearerToken);
    Task<PageResponse<RideRequestResponse>> GetMyRideRequestsAsync(ClaimsPrincipal riderPrincipal, int page, int size, RideRequestStatus? status);
    Task<RideRequestResponse> GetMyRideRequestByUuidAsync(ClaimsPrincipal riderPrincipal, string requestUuid);
    Task<RideRequestResponse> CancelRideRequestAsync(ClaimsPrincipal riderPrincipal, string requestUuid, CancelRideRequestDto? request);
}
