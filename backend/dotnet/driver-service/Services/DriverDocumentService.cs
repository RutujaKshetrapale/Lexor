using DriverService.Data;
using DriverService.DTOs.Response;
using DriverService.Entities;
using DriverService.Enums;
using DriverService.Exceptions;
using DriverService.Storage;
using Microsoft.EntityFrameworkCore;

namespace DriverService.Services;

public interface IDriverDocumentService
{
    Task<DocumentResponse> UploadDocumentAsync(
        string userUuid,
        DocumentType documentType,
        string? documentNumber,
        DateOnly? issueDate,
        DateOnly? expiryDate,
        string? issuingAuthority,
        IFormFile file);

    Task<List<DocumentResponse>> GetDriverDocumentsAsync(string userUuid);
    Task<DocumentResponse> GetDriverDocumentByIdAsync(string userUuid, long documentId);
    Task<(Stream FileStream, string ContentType, string FileName)> DownloadDocumentAsync(string userUuid, long documentId);
    Task<DocumentResponse> ResubmitDocumentAsync(
        string userUuid,
        long documentId,
        IFormFile file,
        string? documentNumber,
        DateOnly? issueDate,
        DateOnly? expiryDate,
        string? issuingAuthority);

    Task DeleteDocumentAsync(string userUuid, long documentId);
}

public class DriverDocumentService : IDriverDocumentService
{
    private readonly LexorDbContext _dbContext;
    private readonly IDocumentStorageService _storageService;
    private readonly IOnboardingPolicyService _onboardingPolicyService;
    private readonly IDriverProfileService _driverProfileService;

    public DriverDocumentService(
        LexorDbContext dbContext,
        IDocumentStorageService storageService,
        IOnboardingPolicyService onboardingPolicyService,
        IDriverProfileService driverProfileService)
    {
        _dbContext = dbContext;
        _storageService = storageService;
        _onboardingPolicyService = onboardingPolicyService;
        _driverProfileService = driverProfileService;
    }

    public async Task<DocumentResponse> UploadDocumentAsync(
        string userUuid,
        DocumentType documentType,
        string? documentNumber,
        DateOnly? issueDate,
        DateOnly? expiryDate,
        string? issuingAuthority,
        IFormFile file)
    {
        var user = await GetUserOrThrowAsync(userUuid);
        var driver = await _driverProfileService.GetOrCreateDriverProfileAsync(user);

        if (user.Status != UserStatus.ACTIVE)
        {
            throw new ForbiddenException($"Account is not active ({user.Status}). Document upload denied.");
        }

        if (issueDate != null && expiryDate != null && expiryDate <= issueDate)
        {
            throw new BadRequestException("Document expiry date must be later than the issue date");
        }

        var stored = await _storageService.StoreAsync(file);

        // Check if an active document of this type already exists
        var existing = await _dbContext.DriverDocuments
            .FirstOrDefaultAsync(d => d.DriverId == driver.Id && d.DocumentType == documentType && d.IsCurrent);

        var nextVersion = 1;
        if (existing != null)
        {
            existing.IsCurrent = false;
            existing.UpdatedAt = DateTime.UtcNow;
            nextVersion = existing.Version + 1;
        }

        var document = new DriverDocument
        {
            DriverId = driver.Id,
            DocumentType = documentType,
            DocumentNumber = string.IsNullOrWhiteSpace(documentNumber) ? null : documentNumber.Trim().ToUpperInvariant(),
            FileName = stored.OriginalFileName,
            FilePath = stored.StoragePath,
            ContentType = stored.ContentType,
            FileSize = stored.FileSize,
            IssueDate = issueDate,
            ExpiryDate = expiryDate,
            IssuingAuthority = string.IsNullOrWhiteSpace(issuingAuthority) ? null : issuingAuthority.Trim(),
            VerificationStatus = DocumentVerificationStatus.PENDING_REVIEW,
            Version = nextVersion,
            IsCurrent = true,
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        _dbContext.DriverDocuments.Add(document);

        // Re-evaluate onboarding readiness
        await _onboardingPolicyService.EvaluateAndSyncDriverOnboardingStatusAsync(driver);
        await _dbContext.SaveChangesAsync();

        return MapToResponse(document);
    }

    public async Task<List<DocumentResponse>> GetDriverDocumentsAsync(string userUuid)
    {
        var user = await GetUserOrThrowAsync(userUuid);
        var driver = await _driverProfileService.GetOrCreateDriverProfileAsync(user);

        var docs = await _dbContext.DriverDocuments
            .Where(d => d.DriverId == driver.Id && d.IsCurrent)
            .OrderByDescending(d => d.CreatedAt)
            .ToListAsync();

        return docs.Select(MapToResponse).ToList();
    }

    public async Task<DocumentResponse> GetDriverDocumentByIdAsync(string userUuid, long documentId)
    {
        var user = await GetUserOrThrowAsync(userUuid);
        var driver = await _driverProfileService.GetOrCreateDriverProfileAsync(user);

        var document = await _dbContext.DriverDocuments.FindAsync(documentId);
        if (document == null)
        {
            throw new ResourceNotFoundException($"Document not found with ID: {documentId}");
        }

        ValidateOwnership(driver, document);
        return MapToResponse(document);
    }

    public async Task<(Stream FileStream, string ContentType, string FileName)> DownloadDocumentAsync(string userUuid, long documentId)
    {
        var user = await GetUserOrThrowAsync(userUuid);
        var driver = await _driverProfileService.GetOrCreateDriverProfileAsync(user);

        var document = await _dbContext.DriverDocuments.FindAsync(documentId);
        if (document == null)
        {
            throw new ResourceNotFoundException($"Document not found with ID: {documentId}");
        }

        ValidateOwnership(driver, document);
        var stream = _storageService.LoadAsStream(document.FilePath);
        return (stream, document.ContentType, document.FileName);
    }

    public async Task<DocumentResponse> ResubmitDocumentAsync(
        string userUuid,
        long documentId,
        IFormFile file,
        string? documentNumber,
        DateOnly? issueDate,
        DateOnly? expiryDate,
        string? issuingAuthority)
    {
        var user = await GetUserOrThrowAsync(userUuid);
        var driver = await _driverProfileService.GetOrCreateDriverProfileAsync(user);

        var targetDoc = await _dbContext.DriverDocuments.FindAsync(documentId);
        if (targetDoc == null)
        {
            throw new ResourceNotFoundException($"Document not found with ID: {documentId}");
        }

        ValidateOwnership(driver, targetDoc);

        if (targetDoc.VerificationStatus == DocumentVerificationStatus.VERIFIED)
        {
            throw new BadRequestException("Document is already VERIFIED. Standard re-upload is required to supersede verified documents.");
        }

        var stored = await _storageService.StoreAsync(file);

        targetDoc.IsCurrent = false;
        targetDoc.UpdatedAt = DateTime.UtcNow;

        var nextVersion = targetDoc.Version + 1;
        var newVersion = new DriverDocument
        {
            DriverId = driver.Id,
            DocumentType = targetDoc.DocumentType,
            DocumentNumber = !string.IsNullOrWhiteSpace(documentNumber) ? documentNumber.Trim().ToUpperInvariant() : targetDoc.DocumentNumber,
            FileName = stored.OriginalFileName,
            FilePath = stored.StoragePath,
            ContentType = stored.ContentType,
            FileSize = stored.FileSize,
            IssueDate = issueDate ?? targetDoc.IssueDate,
            ExpiryDate = expiryDate ?? targetDoc.ExpiryDate,
            IssuingAuthority = !string.IsNullOrWhiteSpace(issuingAuthority) ? issuingAuthority.Trim() : targetDoc.IssuingAuthority,
            VerificationStatus = DocumentVerificationStatus.PENDING_REVIEW,
            Version = nextVersion,
            IsCurrent = true,
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        _dbContext.DriverDocuments.Add(newVersion);

        // Record history
        _dbContext.DriverVerificationHistories.Add(new DriverVerificationHistory
        {
            DriverId = driver.Id,
            Document = newVersion,
            ActionType = "DOCUMENT_RESUBMISSION",
            PreviousStatus = targetDoc.VerificationStatus.ToString(),
            NewStatus = DocumentVerificationStatus.PENDING_REVIEW.ToString(),
            ReviewedByAdminId = user.Id,
            Reason = $"Driver resubmitted document following {targetDoc.VerificationStatus}",
            CreatedAt = DateTime.UtcNow
        });

        await _onboardingPolicyService.EvaluateAndSyncDriverOnboardingStatusAsync(driver);
        await _dbContext.SaveChangesAsync();

        return MapToResponse(newVersion);
    }

    public async Task DeleteDocumentAsync(string userUuid, long documentId)
    {
        var user = await GetUserOrThrowAsync(userUuid);
        var driver = await _driverProfileService.GetOrCreateDriverProfileAsync(user);

        var document = await _dbContext.DriverDocuments.FindAsync(documentId);
        if (document == null)
        {
            throw new ResourceNotFoundException($"Document not found with ID: {documentId}");
        }

        ValidateOwnership(driver, document);

        if (document.VerificationStatus == DocumentVerificationStatus.VERIFIED)
        {
            throw new BadRequestException("Verified compliance documents cannot be deleted by drivers.");
        }

        // Logical removal to preserve audit integrity
        document.IsCurrent = false;
        document.UpdatedAt = DateTime.UtcNow;

        await _onboardingPolicyService.EvaluateAndSyncDriverOnboardingStatusAsync(driver);
        await _dbContext.SaveChangesAsync();
    }

    private async Task<User> GetUserOrThrowAsync(string uuid)
    {
        var user = await _dbContext.Users.FirstOrDefaultAsync(u => u.Uuid == uuid);
        if (user == null)
        {
            throw new ResourceNotFoundException($"User not found with UUID: {uuid}");
        }
        return user;
    }

    private static void ValidateOwnership(DriverProfile driver, DriverDocument document)
    {
        if (document.DriverId != driver.Id)
        {
            throw new ForbiddenException("Access denied: You do not own this document");
        }
    }

    private static DocumentResponse MapToResponse(DriverDocument doc)
    {
        return new DocumentResponse
        {
            Id = doc.Id,
            DriverId = doc.DriverId,
            DocumentType = doc.DocumentType,
            DocumentNumber = MaskSensitiveIdentifier(doc.DocumentNumber),
            FileName = doc.FileName,
            ContentType = doc.ContentType,
            FileSize = doc.FileSize,
            IssueDate = doc.IssueDate,
            ExpiryDate = doc.ExpiryDate,
            IssuingAuthority = doc.IssuingAuthority,
            VerificationStatus = doc.VerificationStatus,
            RejectionReason = doc.RejectionReason,
            ReviewNotes = doc.ReviewNotes,
            ReviewedByAdminId = doc.ReviewedByAdminId,
            ReviewedAt = doc.ReviewedAt,
            Version = doc.Version,
            IsCurrent = doc.IsCurrent,
            CreatedAt = doc.CreatedAt,
            UpdatedAt = doc.UpdatedAt
        };
    }

    private static string? MaskSensitiveIdentifier(string? number)
    {
        if (string.IsNullOrWhiteSpace(number)) return null;
        if (number.Length <= 4) return "****";
        return "****" + number[^4..];
    }
}
