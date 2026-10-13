using System.Security.Claims;
using VehicleService.DTOs.Request;
using VehicleService.DTOs.Response;
using VehicleService.Enums;

namespace VehicleService.Services;

public interface IVehicleAdminService
{
    Task<PagedResponse<VehicleResponse>> ListVehiclesAsync(
        VehicleVerificationStatus? verificationStatus,
        bool? isActive,
        string? rideTypeCategory,
        string? search,
        int page,
        int size,
        string sortBy,
        string sortDir);

    Task<PagedResponse<VehicleResponse>> ListPendingVehiclesAsync(
        int page,
        int size,
        string sortBy,
        string sortDir);

    Task<VehicleResponse> GetVehicleDetailsAsync(string vehicleUuid);

    Task<List<VehicleDocumentResponse>> GetVehicleDocumentsAsync(string vehicleUuid);

    Task<(Stream Stream, string ContentType, string FileName)> DownloadDocumentAsync(string vehicleUuid, long documentId);

    Task<VehicleDocumentResponse> ReviewDocumentAsync(
        ClaimsPrincipal adminUser,
        string vehicleUuid,
        long documentId,
        ReviewVehicleDocumentRequest request);

    Task<VehicleResponse> ReviewVehicleAsync(
        ClaimsPrincipal adminUser,
        string vehicleUuid,
        ReviewVehicleRequest request);

    Task<VehicleResponse> SuspendOrReinstateVehicleAsync(
        ClaimsPrincipal adminUser,
        string vehicleUuid,
        SuspendVehicleRequest request);

    Task<List<VehicleVerificationHistoryResponse>> GetVerificationHistoryAsync(string vehicleUuid);

    Task<DriverVehicleAssignmentResponse> AssignDriverAsync(
        ClaimsPrincipal adminUser,
        string vehicleUuid,
        AssignDriverRequest request);

    Task<DriverVehicleAssignmentResponse> UnassignDriverAsync(
        ClaimsPrincipal adminUser,
        string vehicleUuid,
        long assignmentId,
        string? reason);

    Task<List<DriverVehicleAssignmentResponse>> GetAssignmentHistoryAsync(string vehicleUuid);
}
