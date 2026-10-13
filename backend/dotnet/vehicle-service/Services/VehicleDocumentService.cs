using System.Security.Claims;
using Microsoft.AspNetCore.Http;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;
using VehicleService.Data;
using VehicleService.DTOs.Response;
using VehicleService.Entities;
using VehicleService.Enums;
using VehicleService.Exceptions;
using VehicleService.Storage;

namespace VehicleService.Services;

public class VehicleDocumentService : IVehicleDocumentService
{
    private readonly LexorDbContext _dbContext;
    private readonly IDocumentStorageService _storageService;
    private readonly ILogger<VehicleDocumentService> _logger;

    public VehicleDocumentService(
        LexorDbContext dbContext,
        IDocumentStorageService storageService,
        ILogger<VehicleDocumentService> logger)
    {
        _dbContext = dbContext;
        _storageService = storageService;
        _logger = logger;
    }

    public async Task<VehicleDocumentResponse> UploadDocumentAsync(
        ClaimsPrincipal user,
        string vehicleUuid,
        VehicleDocumentType documentType,
        string? documentNumber,
        DateOnly? issueDate,
        DateOnly? expiryDate,
        string? issuingAuthority,
        IFormFile file)
    {
        var vehicle = await GetVehicleAndVerifyOwnershipAsync(user, vehicleUuid);

        // Store file securely (validates magic bytes, extension, file size, path traversal)
        var stored = await _storageService.StoreAsync(file);

        // Archive previous current version of this document type if one exists
        var existing = await _dbContext.VehicleDocuments
            .FirstOrDefaultAsync(d => d.VehicleId == vehicle.Id && d.DocumentType == documentType && d.IsCurrent);

        var version = 1;
        if (existing != null)
        {
            existing.IsCurrent = false;
            version = existing.Version + 1;
            _logger.LogInformation("Archived previous version {Version} of document {DocumentType} for vehicle {VehicleUuid}",
                existing.Version, documentType, vehicle.Uuid);
        }

        var doc = new VehicleDocument
        {
            VehicleId = vehicle.Id,
            Vehicle = vehicle,
            DocumentType = documentType,
            DocumentNumber = documentNumber,
            FileName = stored.OriginalFileName,
            FilePath = stored.StoragePath,
            ContentType = stored.ContentType,
            FileSize = stored.FileSize,
            IssueDate = issueDate,
            ExpiryDate = expiryDate,
            IssuingAuthority = issuingAuthority,
            VerificationStatus = DocumentVerificationStatus.PENDING_REVIEW,
            Version = version,
            IsCurrent = true,
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        _dbContext.VehicleDocuments.Add(doc);
        await _dbContext.SaveChangesAsync();

        _logger.LogInformation("Uploaded vehicle document ID {DocId} ({DocumentType}, version {Version}) for vehicle {VehicleUuid}",
            doc.Id, documentType, version, vehicle.Uuid);

        return MapToResponse(doc, vehicle.Uuid);
    }

    public async Task<List<VehicleDocumentResponse>> GetVehicleDocumentsAsync(ClaimsPrincipal user, string vehicleUuid)
    {
        var vehicle = await GetVehicleAndVerifyOwnershipAsync(user, vehicleUuid);

        var documents = await _dbContext.VehicleDocuments
            .Include(d => d.ReviewedByAdmin)
            .Where(d => d.VehicleId == vehicle.Id && d.IsCurrent)
            .OrderBy(d => d.DocumentType)
            .ToListAsync();

        return documents.Select(d => MapToResponse(d, vehicle.Uuid)).ToList();
    }

    public async Task<VehicleDocumentResponse> GetDocumentByIdAsync(ClaimsPrincipal user, string vehicleUuid, long documentId)
    {
        var vehicle = await GetVehicleAndVerifyOwnershipAsync(user, vehicleUuid);

        var document = await _dbContext.VehicleDocuments
            .Include(d => d.ReviewedByAdmin)
            .FirstOrDefaultAsync(d => d.Id == documentId && d.VehicleId == vehicle.Id);

        if (document == null)
        {
            throw new ResourceNotFoundException($"Document not found with ID: {documentId}");
        }

        return MapToResponse(document, vehicle.Uuid);
    }

    public async Task<(Stream Stream, string ContentType, string FileName)> DownloadDocumentAsync(
        ClaimsPrincipal user,
        string vehicleUuid,
        long documentId)
    {
        var vehicle = await GetVehicleAndVerifyOwnershipAsync(user, vehicleUuid);

        var document = await _dbContext.VehicleDocuments
            .FirstOrDefaultAsync(d => d.Id == documentId && d.VehicleId == vehicle.Id);

        if (document == null)
        {
            throw new ResourceNotFoundException($"Document not found with ID: {documentId}");
        }

        var stream = _storageService.LoadAsStream(document.FilePath);
        return (stream, document.ContentType, document.FileName);
    }

    public async Task<VehicleDocumentResponse> ResubmitDocumentAsync(
        ClaimsPrincipal user,
        string vehicleUuid,
        long documentId,
        string? documentNumber,
        DateOnly? issueDate,
        DateOnly? expiryDate,
        string? issuingAuthority,
        IFormFile file)
    {
        var vehicle = await GetVehicleAndVerifyOwnershipAsync(user, vehicleUuid);

        var oldDoc = await _dbContext.VehicleDocuments
            .FirstOrDefaultAsync(d => d.Id == documentId && d.VehicleId == vehicle.Id);

        if (oldDoc == null)
        {
            throw new ResourceNotFoundException($"Document not found with ID: {documentId}");
        }

        if (oldDoc.VerificationStatus == DocumentVerificationStatus.VERIFIED)
        {
            throw new BadRequestException("Cannot resubmit an already verified document");
        }

        var stored = await _storageService.StoreAsync(file);

        // Mark old document as non-current
        oldDoc.IsCurrent = false;
        oldDoc.UpdatedAt = DateTime.UtcNow;

        var newDoc = new VehicleDocument
        {
            VehicleId = vehicle.Id,
            Vehicle = vehicle,
            DocumentType = oldDoc.DocumentType,
            DocumentNumber = documentNumber ?? oldDoc.DocumentNumber,
            FileName = stored.OriginalFileName,
            FilePath = stored.StoragePath,
            ContentType = stored.ContentType,
            FileSize = stored.FileSize,
            IssueDate = issueDate ?? oldDoc.IssueDate,
            ExpiryDate = expiryDate ?? oldDoc.ExpiryDate,
            IssuingAuthority = issuingAuthority ?? oldDoc.IssuingAuthority,
            VerificationStatus = DocumentVerificationStatus.PENDING_REVIEW,
            Version = oldDoc.Version + 1,
            IsCurrent = true,
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        _dbContext.VehicleDocuments.Add(newDoc);
        await _dbContext.SaveChangesAsync();

        _logger.LogInformation("Resubmitted document ID {OldDocId} as new version {Version} for vehicle {VehicleUuid}",
            oldDoc.Id, newDoc.Version, vehicle.Uuid);

        return MapToResponse(newDoc, vehicle.Uuid);
    }

    public async Task DeleteDocumentAsync(ClaimsPrincipal user, string vehicleUuid, long documentId)
    {
        var vehicle = await GetVehicleAndVerifyOwnershipAsync(user, vehicleUuid);

        var document = await _dbContext.VehicleDocuments
            .FirstOrDefaultAsync(d => d.Id == documentId && d.VehicleId == vehicle.Id);

        if (document == null)
        {
            throw new ResourceNotFoundException($"Document not found with ID: {documentId}");
        }

        if (document.VerificationStatus == DocumentVerificationStatus.VERIFIED)
        {
            throw new BadRequestException("Cannot delete an already verified document required for vehicle compliance");
        }

        // Soft delete: retain record for auditability
        document.IsCurrent = false;
        document.UpdatedAt = DateTime.UtcNow;
        await _dbContext.SaveChangesAsync();

        _logger.LogInformation("Soft-deleted document ID {DocId} for vehicle {VehicleUuid}", documentId, vehicle.Uuid);
    }

    private async Task<Vehicle> GetVehicleAndVerifyOwnershipAsync(ClaimsPrincipal user, string vehicleUuid)
    {
        var vehicle = await _dbContext.Vehicles
            .FirstOrDefaultAsync(v => v.Uuid == vehicleUuid);

        if (vehicle == null)
        {
            throw new ResourceNotFoundException($"Vehicle not found with UUID: {vehicleUuid}");
        }

        var isAdmin = user.IsInRole("ROLE_ADMIN") || user.IsInRole("ADMIN");

        if (!isAdmin)
        {
            var userIdStr = user.FindFirst("userId")?.Value;
            var userUuid = user.FindFirst("sub")?.Value 
                           ?? user.FindFirst(ClaimTypes.NameIdentifier)?.Value
                           ?? user.FindFirst("uuid")?.Value;

            DriverProfile? driver = null;
            if (long.TryParse(userIdStr, out var userId))
            {
                driver = await _dbContext.DriverProfiles.FirstOrDefaultAsync(d => d.UserId == userId);
            }
            if (driver == null && !string.IsNullOrEmpty(userUuid))
            {
                driver = await _dbContext.DriverProfiles
                    .Include(d => d.User)
                    .FirstOrDefaultAsync(d => d.User.Uuid == userUuid);
            }

            if (driver == null)
            {
                throw new ForbiddenException("No driver profile found for authenticated user");
            }

            if (vehicle.DriverId != driver.Id)
            {
                throw new ForbiddenException("You do not have permission to access or manage this vehicle's documents");
            }
        }

        return vehicle;
    }

    private static VehicleDocumentResponse MapToResponse(VehicleDocument doc, string vehicleUuid)
    {
        return new VehicleDocumentResponse
        {
            Id = doc.Id,
            VehicleUuid = vehicleUuid,
            DocumentType = doc.DocumentType,
            DocumentNumber = doc.DocumentNumber,
            FileName = doc.FileName,
            ContentType = doc.ContentType,
            FileSize = doc.FileSize,
            IssueDate = doc.IssueDate,
            ExpiryDate = doc.ExpiryDate,
            IssuingAuthority = doc.IssuingAuthority,
            VerificationStatus = doc.VerificationStatus,
            RejectionReason = doc.RejectionReason,
            ReviewNotes = doc.ReviewNotes,
            ReviewedByAdminName = doc.ReviewedByAdmin?.FullName,
            ReviewedAt = doc.ReviewedAt,
            Version = doc.Version,
            IsCurrent = doc.IsCurrent,
            CreatedAt = doc.CreatedAt,
            UpdatedAt = doc.UpdatedAt
        };
    }
}
