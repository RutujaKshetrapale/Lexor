using System.Security.Claims;
using Microsoft.AspNetCore.Http;
using VehicleService.DTOs.Response;
using VehicleService.Enums;

namespace VehicleService.Services;

public interface IVehicleDocumentService
{
    Task<VehicleDocumentResponse> UploadDocumentAsync(
        ClaimsPrincipal user,
        string vehicleUuid,
        VehicleDocumentType documentType,
        string? documentNumber,
        DateOnly? issueDate,
        DateOnly? expiryDate,
        string? issuingAuthority,
        IFormFile file);

    Task<List<VehicleDocumentResponse>> GetVehicleDocumentsAsync(ClaimsPrincipal user, string vehicleUuid);

    Task<VehicleDocumentResponse> GetDocumentByIdAsync(ClaimsPrincipal user, string vehicleUuid, long documentId);

    Task<(Stream Stream, string ContentType, string FileName)> DownloadDocumentAsync(
        ClaimsPrincipal user,
        string vehicleUuid,
        long documentId);

    Task<VehicleDocumentResponse> ResubmitDocumentAsync(
        ClaimsPrincipal user,
        string vehicleUuid,
        long documentId,
        string? documentNumber,
        DateOnly? issueDate,
        DateOnly? expiryDate,
        string? issuingAuthority,
        IFormFile file);

    Task DeleteDocumentAsync(ClaimsPrincipal user, string vehicleUuid, long documentId);
}
