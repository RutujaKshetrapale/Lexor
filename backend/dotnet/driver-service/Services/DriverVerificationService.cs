using DriverService.Data;
using DriverService.DTOs.Request;
using DriverService.DTOs.Response;
using DriverService.Entities;
using DriverService.Enums;
using DriverService.Exceptions;
using DriverService.Storage;
using Microsoft.EntityFrameworkCore;

namespace DriverService.Services;

public interface IDriverVerificationService
{
    Task<PagedResponse<DriverProfileResponse>> ListDriversAsync(
        OnboardingStatus? verificationStatus,
        UserStatus? accountStatus,
        DriverStatus? availabilityStatus,
        string? search,
        int page,
        int size,
        string sortBy,
        string sortDir);

    Task<DriverProfileResponse> GetDriverByUuidAsync(string driverUuid);
    Task<List<DocumentResponse>> GetDriverDocumentsByUuidAsync(string driverUuid);

    Task<PagedResponse<DocumentResponse>> ListAwaitingDocumentsAsync(
        DocumentVerificationStatus? status,
        DocumentType? documentType,
        string? driverUuid,
        int page,
        int size);

    Task<(Stream FileStream, string ContentType, string FileName)> DownloadDocumentForAdminAsync(long documentId);
    Task<DocumentResponse> ReviewDocumentAsync(string adminUuid, long documentId, ReviewDocumentRequest request);
    Task<DriverProfileResponse> ReviewDriverVerificationAsync(string adminUuid, string driverUuid, VerifyDriverRequest request);
    Task<DriverProfileResponse> UpdateDriverSuspensionAsync(string adminUuid, string driverUuid, DriverSuspensionRequest request);
    Task<List<VerificationHistoryResponse>> GetDriverVerificationHistoryAsync(string driverUuid);
}

public class DriverVerificationService : IDriverVerificationService
{
    private readonly LexorDbContext _dbContext;
    private readonly IDocumentStorageService _storageService;
    private readonly IDriverProfileService _driverProfileService;
    private readonly IOnboardingPolicyService _onboardingPolicyService;

    public DriverVerificationService(
        LexorDbContext dbContext,
        IDocumentStorageService storageService,
        IDriverProfileService driverProfileService,
        IOnboardingPolicyService onboardingPolicyService)
    {
        _dbContext = dbContext;
        _storageService = storageService;
        _driverProfileService = driverProfileService;
        _onboardingPolicyService = onboardingPolicyService;
    }

    public async Task<PagedResponse<DriverProfileResponse>> ListDriversAsync(
        OnboardingStatus? verificationStatus,
        UserStatus? accountStatus,
        DriverStatus? availabilityStatus,
        string? search,
        int page,
        int size,
        string sortBy,
        string sortDir)
    {
        var query = _dbContext.DriverProfiles
            .Include(p => p.User)
            .AsQueryable();

        if (verificationStatus.HasValue)
        {
            query = query.Where(p => p.OnboardingStatus == verificationStatus.Value);
        }

        if (availabilityStatus.HasValue)
        {
            query = query.Where(p => p.Status == availabilityStatus.Value);
        }

        if (accountStatus.HasValue)
        {
            query = query.Where(p => p.User.Status == accountStatus.Value);
        }

        if (!string.IsNullOrWhiteSpace(search))
        {
            var term = search.Trim().ToLowerInvariant();
            query = query.Where(p =>
                p.User.FirstName.ToLower().Contains(term) ||
                p.User.LastName.ToLower().Contains(term) ||
                p.User.Email.ToLower().Contains(term) ||
                p.User.PhoneNumber.ToLower().Contains(term) ||
                (p.LicenseNumber != null && p.LicenseNumber.ToLower().Contains(term)));
        }

        var totalElements = await query.LongCountAsync();

        // Sorting
        query = (sortBy?.ToLowerInvariant(), sortDir?.ToLowerInvariant()) switch
        {
            ("createdat", "asc") => query.OrderBy(p => p.CreatedAt),
            ("licenseexpirydate", "asc") => query.OrderBy(p => p.LicenseExpiryDate),
            ("licenseexpirydate", "desc") => query.OrderByDescending(p => p.LicenseExpiryDate),
            ("rating", "asc") => query.OrderBy(p => p.Rating),
            ("rating", "desc") => query.OrderByDescending(p => p.Rating),
            _ => query.OrderByDescending(p => p.CreatedAt)
        };

        var items = await query
            .Skip(page * size)
            .Take(size)
            .ToListAsync();

        var content = new List<DriverProfileResponse>();
        foreach (var item in items)
        {
            content.Add(await _driverProfileService.MapToResponseAsync(item));
        }

        var totalPages = (int)Math.Ceiling((double)totalElements / size);

        return new PagedResponse<DriverProfileResponse>
        {
            Content = content,
            Page = page,
            Size = size,
            TotalElements = totalElements,
            TotalPages = totalPages,
            Last = page >= totalPages - 1
        };
    }

    public async Task<DriverProfileResponse> GetDriverByUuidAsync(string driverUuid)
    {
        var driver = await _dbContext.DriverProfiles
            .Include(p => p.User)
            .FirstOrDefaultAsync(p => p.User.Uuid == driverUuid);

        if (driver == null)
        {
            throw new ResourceNotFoundException($"Driver profile not found with user UUID: {driverUuid}");
        }

        return await _driverProfileService.MapToResponseAsync(driver);
    }

    public async Task<List<DocumentResponse>> GetDriverDocumentsByUuidAsync(string driverUuid)
    {
        var driver = await _dbContext.DriverProfiles
            .Include(p => p.User)
            .FirstOrDefaultAsync(p => p.User.Uuid == driverUuid);

        if (driver == null)
        {
            throw new ResourceNotFoundException($"Driver not found with UUID: {driverUuid}");
        }

        var docs = await _dbContext.DriverDocuments
            .Where(d => d.DriverId == driver.Id)
            .OrderByDescending(d => d.CreatedAt)
            .ToListAsync();

        return docs.Select(MapDocToResponseAdmin).ToList();
    }

    public async Task<PagedResponse<DocumentResponse>> ListAwaitingDocumentsAsync(
        DocumentVerificationStatus? status,
        DocumentType? documentType,
        string? driverUuid,
        int page,
        int size)
    {
        var query = _dbContext.DriverDocuments
            .Include(d => d.Driver)
            .ThenInclude(p => p.User)
            .Where(d => d.IsCurrent);

        var filterStatus = status ?? DocumentVerificationStatus.PENDING_REVIEW;
        query = query.Where(d => d.VerificationStatus == filterStatus);

        if (documentType.HasValue)
        {
            query = query.Where(d => d.DocumentType == documentType.Value);
        }

        if (!string.IsNullOrWhiteSpace(driverUuid))
        {
            query = query.Where(d => d.Driver.User.Uuid == driverUuid.Trim());
        }

        var totalElements = await query.LongCountAsync();
        var items = await query
            .OrderByDescending(d => d.CreatedAt)
            .Skip(page * size)
            .Take(size)
            .ToListAsync();

        var content = items.Select(MapDocToResponseAdmin).ToList();
        var totalPages = (int)Math.Ceiling((double)totalElements / size);

        return new PagedResponse<DocumentResponse>
        {
            Content = content,
            Page = page,
            Size = size,
            TotalElements = totalElements,
            TotalPages = totalPages,
            Last = page >= totalPages - 1
        };
    }

    public async Task<(Stream FileStream, string ContentType, string FileName)> DownloadDocumentForAdminAsync(long documentId)
    {
        var document = await _dbContext.DriverDocuments.FindAsync(documentId);
        if (document == null)
        {
            throw new ResourceNotFoundException($"Document not found with ID: {documentId}");
        }

        var stream = _storageService.LoadAsStream(document.FilePath);
        return (stream, document.ContentType, document.FileName);
    }

    public async Task<DocumentResponse> ReviewDocumentAsync(string adminUuid, long documentId, ReviewDocumentRequest request)
    {
        var admin = await _dbContext.Users.FirstOrDefaultAsync(u => u.Uuid == adminUuid);
        if (admin == null)
        {
            throw new ResourceNotFoundException($"Admin user not found with UUID: {adminUuid}");
        }

        var document = await _dbContext.DriverDocuments
            .Include(d => d.Driver)
            .FirstOrDefaultAsync(d => d.Id == documentId);

        if (document == null)
        {
            throw new ResourceNotFoundException($"Document not found with ID: {documentId}");
        }

        if (request.Status == DocumentVerificationStatus.REJECTED || request.Status == DocumentVerificationStatus.RESUBMISSION_REQUIRED)
        {
            if (string.IsNullOrWhiteSpace(request.RejectionReason) && string.IsNullOrWhiteSpace(request.ReviewNotes))
            {
                throw new BadRequestException("Rejection reason or review notes are mandatory when rejecting a document or requesting resubmission");
            }
        }

        var previousStatus = document.VerificationStatus.ToString();
        document.VerificationStatus = request.Status;
        document.RejectionReason = request.RejectionReason;
        document.ReviewNotes = request.ReviewNotes;
        document.ReviewedByAdminId = admin.Id;
        document.ReviewedAt = DateTime.UtcNow;
        document.UpdatedAt = DateTime.UtcNow;

        _dbContext.DriverVerificationHistories.Add(new DriverVerificationHistory
        {
            DriverId = document.DriverId,
            Document = document,
            ActionType = "DOCUMENT_REVIEW",
            PreviousStatus = previousStatus,
            NewStatus = request.Status.ToString(),
            ReviewedByAdminId = admin.Id,
            Reason = !string.IsNullOrWhiteSpace(request.RejectionReason) ? request.RejectionReason : request.ReviewNotes,
            CreatedAt = DateTime.UtcNow
        });

        await _onboardingPolicyService.EvaluateAndSyncDriverOnboardingStatusAsync(document.Driver);
        await _dbContext.SaveChangesAsync();

        return MapDocToResponseAdmin(document);
    }

    public async Task<DriverProfileResponse> ReviewDriverVerificationAsync(string adminUuid, string driverUuid, VerifyDriverRequest request)
    {
        var admin = await _dbContext.Users.FirstOrDefaultAsync(u => u.Uuid == adminUuid);
        if (admin == null)
        {
            throw new ResourceNotFoundException($"Admin user not found with UUID: {adminUuid}");
        }

        var driver = await _dbContext.DriverProfiles
            .Include(p => p.User)
            .FirstOrDefaultAsync(p => p.User.Uuid == driverUuid);

        if (driver == null)
        {
            throw new ResourceNotFoundException($"Driver profile not found with user UUID: {driverUuid}");
        }

        var previousStatus = driver.OnboardingStatus.ToString();

        if (request.Status == OnboardingStatus.VERIFIED)
        {
            if (!await _onboardingPolicyService.IsProfileCompleteAsync(driver))
            {
                throw new BadRequestException("Cannot verify driver: Personal or licence profile information is incomplete");
            }
            if (!await _onboardingPolicyService.AreAllRequiredDocumentsVerifiedAsync(driver.Id))
            {
                throw new BadRequestException("Cannot verify driver: All required documents must be marked VERIFIED prior to approval");
            }
            driver.IsVerified = true;
            driver.VerifiedAt = DateTime.UtcNow;
            driver.OnboardingStatus = OnboardingStatus.VERIFIED;
            driver.RejectionReason = null;
        }
        else if (request.Status == OnboardingStatus.REJECTED)
        {
            if (string.IsNullOrWhiteSpace(request.Notes))
            {
                throw new BadRequestException("Rejection explanation notes are required when setting driver status to REJECTED");
            }
            driver.IsVerified = false;
            driver.OnboardingStatus = OnboardingStatus.REJECTED;
            driver.RejectionReason = request.Notes.Trim();
        }
        else if (request.Status == OnboardingStatus.UNDER_REVIEW)
        {
            driver.OnboardingStatus = OnboardingStatus.UNDER_REVIEW;
        }
        else
        {
            throw new BadRequestException($"Unsupported administrative verification status: {request.Status}");
        }

        driver.UpdatedAt = DateTime.UtcNow;

        _dbContext.DriverVerificationHistories.Add(new DriverVerificationHistory
        {
            DriverId = driver.Id,
            DocumentId = null,
            ActionType = "DRIVER_VERIFICATION",
            PreviousStatus = previousStatus,
            NewStatus = request.Status.ToString(),
            ReviewedByAdminId = admin.Id,
            Reason = request.Notes,
            CreatedAt = DateTime.UtcNow
        });

        await _dbContext.SaveChangesAsync();

        return await _driverProfileService.MapToResponseAsync(driver);
    }

    public async Task<DriverProfileResponse> UpdateDriverSuspensionAsync(string adminUuid, string driverUuid, DriverSuspensionRequest request)
    {
        var admin = await _dbContext.Users.FirstOrDefaultAsync(u => u.Uuid == adminUuid);
        if (admin == null)
        {
            throw new ResourceNotFoundException($"Admin user not found with UUID: {adminUuid}");
        }

        var driver = await _dbContext.DriverProfiles
            .Include(p => p.User)
            .FirstOrDefaultAsync(p => p.User.Uuid == driverUuid);

        if (driver == null)
        {
            throw new ResourceNotFoundException($"Driver profile not found with user UUID: {driverUuid}");
        }

        var previousStatus = driver.OnboardingStatus.ToString();

        if (request.Suspended == true)
        {
            driver.Status = DriverStatus.SUSPENDED;
            driver.OnboardingStatus = OnboardingStatus.SUSPENDED;
            driver.RejectionReason = request.Reason;
        }
        else
        {
            // Reinstating
            driver.Status = DriverStatus.OFFLINE;
            if (await _onboardingPolicyService.AreAllRequiredDocumentsVerifiedAsync(driver.Id) &&
                await _onboardingPolicyService.IsProfileCompleteAsync(driver))
            {
                driver.OnboardingStatus = OnboardingStatus.VERIFIED;
                driver.IsVerified = true;
            }
            else
            {
                driver.OnboardingStatus = OnboardingStatus.PENDING_REVIEW;
                driver.IsVerified = false;
            }
            driver.RejectionReason = null;
        }

        driver.UpdatedAt = DateTime.UtcNow;

        _dbContext.DriverVerificationHistories.Add(new DriverVerificationHistory
        {
            DriverId = driver.Id,
            DocumentId = null,
            ActionType = "DRIVER_SUSPENSION",
            PreviousStatus = previousStatus,
            NewStatus = driver.OnboardingStatus.ToString(),
            ReviewedByAdminId = admin.Id,
            Reason = request.Reason,
            CreatedAt = DateTime.UtcNow
        });

        await _dbContext.SaveChangesAsync();

        return await _driverProfileService.MapToResponseAsync(driver);
    }

    public async Task<List<VerificationHistoryResponse>> GetDriverVerificationHistoryAsync(string driverUuid)
    {
        var history = await _dbContext.DriverVerificationHistories
            .Include(h => h.Driver)
            .ThenInclude(p => p.User)
            .Include(h => h.ReviewedByAdmin)
            .Where(h => h.Driver.User.Uuid == driverUuid)
            .OrderByDescending(h => h.CreatedAt)
            .ToListAsync();

        return history.Select(h => new VerificationHistoryResponse
        {
            Id = h.Id,
            DriverId = h.DriverId,
            DocumentId = h.DocumentId,
            ActionType = h.ActionType,
            PreviousStatus = h.PreviousStatus,
            NewStatus = h.NewStatus,
            ReviewedByAdminId = h.ReviewedByAdminId,
            ReviewedByAdminName = $"{h.ReviewedByAdmin.FirstName} {h.ReviewedByAdmin.LastName}".Trim(),
            Reason = h.Reason,
            CreatedAt = h.CreatedAt
        }).ToList();
    }

    private static DocumentResponse MapDocToResponseAdmin(DriverDocument doc)
    {
        return new DocumentResponse
        {
            Id = doc.Id,
            DriverId = doc.DriverId,
            DocumentType = doc.DocumentType,
            DocumentNumber = doc.DocumentNumber, // Unmasked for admin
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
}
