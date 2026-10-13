using System.Security.Claims;
using VehicleService.DTOs.Request;
using VehicleService.DTOs.Response;

namespace VehicleService.Services;

public interface IVehicleService
{
    Task<VehicleResponse> RegisterVehicleAsync(ClaimsPrincipal user, RegisterVehicleRequest request);
    Task<List<VehicleResponse>> GetMyVehiclesAsync(ClaimsPrincipal user);
    Task<VehicleResponse> GetVehicleByUuidAsync(ClaimsPrincipal user, string vehicleUuid);
    Task<VehicleResponse> UpdateVehicleAsync(ClaimsPrincipal user, string vehicleUuid, UpdateVehicleRequest request);
    Task<VehicleResponse> UpdateVehicleStatusAsync(ClaimsPrincipal user, string vehicleUuid, bool isActive);
    Task<VehicleResponse> SubmitForVerificationAsync(ClaimsPrincipal user, string vehicleUuid);
    Task<VehicleVerificationStatusResponse> GetVerificationStatusAsync(ClaimsPrincipal user, string vehicleUuid);
    Task<List<VehicleVerificationHistoryResponse>> GetVehicleVerificationHistoryAsync(ClaimsPrincipal user, string vehicleUuid);
    Task<List<DriverVehicleAssignmentResponse>> GetVehicleAssignmentsAsync(ClaimsPrincipal user, string vehicleUuid);
}
