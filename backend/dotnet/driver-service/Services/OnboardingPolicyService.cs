using DriverService.Configuration;
using DriverService.Data;
using DriverService.DTOs.Response;
using DriverService.Entities;
using DriverService.Enums;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace DriverService.Services;

public interface IOnboardingPolicyService
{
    Task<DriverOnboardingResponse> CalculateOnboardingProgressAsync(DriverProfile driver);
    Task<bool> IsProfileCompleteAsync(DriverProfile driver);
    Task<bool> AreAllRequiredDocumentsVerifiedAsync(long driverId);
    Task<bool> AreAllRequiredDocumentsSubmittedAsync(long driverId);
    Task EvaluateAndSyncDriverOnboardingStatusAsync(DriverProfile driver);
}

public class OnboardingPolicyService : IOnboardingPolicyService
{
    private readonly LexorDbContext _dbContext;
    private readonly List<DocumentType> _requiredDocumentTypes;

    public OnboardingPolicyService(
        LexorDbContext dbContext,
        IOptions<OnboardingPolicySettings> policySettings)
    {
        _dbContext = dbContext;

        var list = new List<DocumentType>();
        var configStr = policySettings.Value.RequiredDocuments;
        if (!string.IsNullOrWhiteSpace(configStr))
        {
            foreach (var item in configStr.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
            {
                if (Enum.TryParse<DocumentType>(item, true, out var dt))
                {
                    list.Add(dt);
                }
            }
        }

        if (list.Count == 0)
        {
            list.Add(DocumentType.DRIVING_LICENSE);
            list.Add(DocumentType.GOVERNMENT_ID);
            list.Add(DocumentType.ADDRESS_PROOF);
        }

        _requiredDocumentTypes = list;
    }

    public async Task<DriverOnboardingResponse> CalculateOnboardingProgressAsync(DriverProfile driver)
    {
        var currentDocs = await _dbContext.DriverDocuments
            .Where(d => d.DriverId == driver.Id && d.IsCurrent)
            .ToListAsync();

        var docMap = currentDocs.ToDictionary(d => d.DocumentType);

        var checklist = new List<DocumentRequirementStatus>();
        var outstandingTasks = new List<string>();

        var profileComplete = await IsProfileCompleteAsync(driver);
        var today = DateOnly.FromDateTime(DateTime.UtcNow);

        if (!profileComplete)
        {
            if (string.IsNullOrWhiteSpace(driver.LicenseNumber) || driver.LicenseExpiryDate == null)
            {
                outstandingTasks.Add("Provide valid driving license number and future expiry date");
            }

            if (driver.DateOfBirth == null)
            {
                outstandingTasks.Add("Provide date of birth (driver must be at least 18 years old)");
            }
            else if (GetAge(driver.DateOfBirth.Value, today) < 18)
            {
                outstandingTasks.Add("Driver must be at least 18 years of age");
            }

            if (string.IsNullOrWhiteSpace(driver.ResidentialAddress) || string.IsNullOrWhiteSpace(driver.City))
            {
                outstandingTasks.Add("Provide complete residential address and city");
            }

            var hasEmergency = await _dbContext.EmergencyContacts.AnyAsync(e => e.UserId == driver.UserId);
            if (!hasEmergency)
            {
                outstandingTasks.Add("Add emergency contact information");
            }
        }

        var submittedDocsCount = 0;
        var verifiedDocsCount = 0;
        var allRequiredSubmitted = true;
        var allRequiredVerified = true;
        var anyRejected = false;
        string? latestRejectionReason = null;

        foreach (var type in _requiredDocumentTypes)
        {
            docMap.TryGetValue(type, out var doc);
            var status = doc != null ? doc.VerificationStatus : DocumentVerificationStatus.NOT_SUBMITTED;

            var isSubmitted = status != DocumentVerificationStatus.NOT_SUBMITTED;
            var isVerified = status == DocumentVerificationStatus.VERIFIED;
            var isRejected = status == DocumentVerificationStatus.REJECTED || status == DocumentVerificationStatus.RESUBMISSION_REQUIRED;

            if (isSubmitted)
            {
                submittedDocsCount++;
            }
            else
            {
                allRequiredSubmitted = false;
                outstandingTasks.Add($"Upload required document: {FormatDocTypeName(type)}");
            }

            if (isVerified)
            {
                verifiedDocsCount++;
            }
            else
            {
                allRequiredVerified = false;
            }

            if (isRejected)
            {
                anyRejected = true;
                latestRejectionReason = doc?.RejectionReason;
                outstandingTasks.Add($"Resubmit rejected document: {FormatDocTypeName(type)} (Reason: {doc?.RejectionReason})");
            }

            checklist.Add(new DocumentRequirementStatus
            {
                DocumentType = type,
                Required = true,
                Status = status,
                DocumentId = doc?.Id,
                DocumentNumber = MaskSensitiveNumber(doc?.DocumentNumber),
                RejectionReason = doc?.RejectionReason,
                UploadedAt = doc?.CreatedAt,
                VerifiedAt = doc?.ReviewedAt
            });
        }

        // Add non-required current docs
        foreach (var doc in currentDocs.Where(d => !_requiredDocumentTypes.Contains(d.DocumentType)))
        {
            checklist.Add(new DocumentRequirementStatus
            {
                DocumentType = doc.DocumentType,
                Required = false,
                Status = doc.VerificationStatus,
                DocumentId = doc.Id,
                DocumentNumber = MaskSensitiveNumber(doc.DocumentNumber),
                RejectionReason = doc.RejectionReason,
                UploadedAt = doc.CreatedAt,
                VerifiedAt = doc.ReviewedAt
            });
        }

        var profileScore = profileComplete ? 50 : await CalculateProfileScoreAsync(driver, today);
        var docScore = _requiredDocumentTypes.Count == 0 ? 50 : (int)Math.Round((submittedDocsCount * 50.0) / _requiredDocumentTypes.Count);
        var totalPercentage = Math.Min(100, profileScore + docScore);

        var canSubmit = profileComplete && allRequiredSubmitted && !anyRejected &&
                        (driver.OnboardingStatus == OnboardingStatus.INCOMPLETE || driver.OnboardingStatus == OnboardingStatus.REJECTED);

        var eligibleForOnline = driver.OnboardingStatus == OnboardingStatus.VERIFIED && driver.IsVerified;

        var userUuid = await _dbContext.Users
            .Where(u => u.Id == driver.UserId)
            .Select(u => u.Uuid)
            .FirstOrDefaultAsync();

        return new DriverOnboardingResponse
        {
            DriverUuid = userUuid,
            OnboardingStatus = driver.OnboardingStatus,
            CompletionPercentage = totalPercentage,
            ProfileComplete = profileComplete,
            DocumentsComplete = allRequiredVerified,
            CanSubmitForReview = canSubmit,
            IsEligibleForOnline = eligibleForOnline,
            DocumentChecklist = checklist,
            OutstandingTasks = outstandingTasks,
            RejectionReason = anyRejected ? latestRejectionReason : driver.RejectionReason
        };
    }

    public async Task<bool> IsProfileCompleteAsync(DriverProfile driver)
    {
        if (string.IsNullOrWhiteSpace(driver.LicenseNumber)) return false;
        var today = DateOnly.FromDateTime(DateTime.UtcNow);
        if (driver.LicenseExpiryDate == null || driver.LicenseExpiryDate.Value <= today) return false;
        if (driver.DateOfBirth == null) return false;
        if (GetAge(driver.DateOfBirth.Value, today) < 18) return false;
        if (string.IsNullOrWhiteSpace(driver.ResidentialAddress)) return false;
        if (string.IsNullOrWhiteSpace(driver.City)) return false;
        if (string.IsNullOrWhiteSpace(driver.State)) return false;
        if (string.IsNullOrWhiteSpace(driver.PostalCode)) return false;

        return await _dbContext.EmergencyContacts.AnyAsync(e => e.UserId == driver.UserId);
    }

    public async Task<bool> AreAllRequiredDocumentsVerifiedAsync(long driverId)
    {
        var currentDocs = await _dbContext.DriverDocuments
            .Where(d => d.DriverId == driverId && d.IsCurrent)
            .ToListAsync();

        var docMap = currentDocs.ToDictionary(d => d.DocumentType);

        foreach (var req in _requiredDocumentTypes)
        {
            if (!docMap.TryGetValue(req, out var doc) || doc.VerificationStatus != DocumentVerificationStatus.VERIFIED)
            {
                return false;
            }
        }

        return true;
    }

    public async Task<bool> AreAllRequiredDocumentsSubmittedAsync(long driverId)
    {
        var currentDocs = await _dbContext.DriverDocuments
            .Where(d => d.DriverId == driverId && d.IsCurrent)
            .ToListAsync();

        var docMap = currentDocs.ToDictionary(d => d.DocumentType);

        foreach (var req in _requiredDocumentTypes)
        {
            if (!docMap.TryGetValue(req, out var doc) || doc.VerificationStatus == DocumentVerificationStatus.NOT_SUBMITTED)
            {
                return false;
            }
        }

        return true;
    }

    public async Task EvaluateAndSyncDriverOnboardingStatusAsync(DriverProfile driver)
    {
        if (driver.OnboardingStatus == OnboardingStatus.SUSPENDED)
        {
            return;
        }

        var currentDocs = await _dbContext.DriverDocuments
            .Where(d => d.DriverId == driver.Id && d.IsCurrent)
            .ToListAsync();

        var hasRejected = currentDocs.Any(d =>
            d.VerificationStatus == DocumentVerificationStatus.REJECTED ||
            d.VerificationStatus == DocumentVerificationStatus.RESUBMISSION_REQUIRED);

        if (hasRejected)
        {
            driver.OnboardingStatus = OnboardingStatus.REJECTED;
            driver.IsVerified = false;
            return;
        }

        var profileComplete = await IsProfileCompleteAsync(driver);
        var docsVerified = await AreAllRequiredDocumentsVerifiedAsync(driver.Id);

        if (profileComplete && docsVerified)
        {
            driver.OnboardingStatus = OnboardingStatus.VERIFIED;
            driver.IsVerified = true;
            driver.VerifiedAt ??= DateTime.UtcNow;
        }
        else if (driver.OnboardingStatus == OnboardingStatus.VERIFIED)
        {
            driver.OnboardingStatus = OnboardingStatus.PENDING_REVIEW;
            driver.IsVerified = false;
        }
    }

    private async Task<int> CalculateProfileScoreAsync(DriverProfile driver, DateOnly today)
    {
        var score = 0;
        if (!string.IsNullOrWhiteSpace(driver.LicenseNumber) && driver.LicenseExpiryDate != null) score += 15;
        if (driver.DateOfBirth != null && GetAge(driver.DateOfBirth.Value, today) >= 18) score += 10;
        if (!string.IsNullOrWhiteSpace(driver.ResidentialAddress) && !string.IsNullOrWhiteSpace(driver.City)) score += 15;
        if (await _dbContext.EmergencyContacts.AnyAsync(e => e.UserId == driver.UserId)) score += 10;
        return score;
    }

    private static int GetAge(DateOnly dateOfBirth, DateOnly today)
    {
        var age = today.Year - dateOfBirth.Year;
        if (dateOfBirth > today.AddYears(-age)) age--;
        return age;
    }

    private static string FormatDocTypeName(DocumentType type) => type.ToString().Replace('_', ' ');

    private static string? MaskSensitiveNumber(string? number)
    {
        if (string.IsNullOrWhiteSpace(number)) return null;
        if (number.Length <= 4) return "****";
        return "****" + number[^4..];
    }
}
