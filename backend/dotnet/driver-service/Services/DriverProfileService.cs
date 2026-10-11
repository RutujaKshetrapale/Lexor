using DriverService.Data;
using DriverService.DTOs;
using DriverService.DTOs.Request;
using DriverService.DTOs.Response;
using DriverService.Entities;
using DriverService.Enums;
using DriverService.Exceptions;
using Microsoft.EntityFrameworkCore;

namespace DriverService.Services;

public interface IDriverProfileService
{
    Task<DriverProfileResponse> GetDriverProfileAsync(string userUuid);
    Task<DriverProfileResponse> UpdateDriverProfileAsync(string userUuid, UpdateDriverProfileRequest request);
    Task<DriverProfileResponse> PatchDriverProfileAsync(string userUuid, PatchDriverProfileRequest request);
    Task<DriverOnboardingResponse> GetOnboardingStatusAsync(string userUuid);
    Task<DriverOnboardingResponse> SubmitForReviewAsync(string userUuid);
    Task<DriverAvailabilityResponse> UpdateAvailabilityAsync(string userUuid, DriverAvailabilityRequest request);
    Task<DriverProfile> GetOrCreateDriverProfileAsync(User user);
    Task<DriverProfileResponse> MapToResponseAsync(DriverProfile driver);
}

public class DriverProfileService : IDriverProfileService
{
    private readonly LexorDbContext _dbContext;
    private readonly IOnboardingPolicyService _onboardingPolicyService;

    public DriverProfileService(
        LexorDbContext dbContext,
        IOnboardingPolicyService onboardingPolicyService)
    {
        _dbContext = dbContext;
        _onboardingPolicyService = onboardingPolicyService;
    }

    public async Task<DriverProfileResponse> GetDriverProfileAsync(string userUuid)
    {
        var user = await GetUserOrThrowAsync(userUuid);
        var driver = await GetOrCreateDriverProfileAsync(user);
        return await MapToResponseAsync(driver);
    }

    public async Task<DriverProfileResponse> UpdateDriverProfileAsync(string userUuid, UpdateDriverProfileRequest request)
    {
        var user = await GetUserOrThrowAsync(userUuid);
        var driver = await GetOrCreateDriverProfileAsync(user);

        // Validation: License dates
        if (request.LicenseIssueDate != null && request.LicenseExpiryDate != null)
        {
            if (request.LicenseExpiryDate <= request.LicenseIssueDate)
            {
                throw new BadRequestException("Driving license expiry date must be later than the issue date");
            }
        }

        // Validation: Age >= 18
        if (request.DateOfBirth != null)
        {
            var today = DateOnly.FromDateTime(DateTime.UtcNow);
            var age = GetAge(request.DateOfBirth.Value, today);
            if (age < 18)
            {
                throw new BadRequestException($"Driver must be at least 18 years of age (current age: {age})");
            }
        }

        // Check unique license number
        var cleanLicense = request.LicenseNumber.Trim().ToUpperInvariant();
        var exists = await _dbContext.DriverProfiles.AnyAsync(p => p.LicenseNumber == cleanLicense && p.Id != driver.Id);
        if (exists)
        {
            throw new DuplicateResourceException($"Driving license number is already registered: {cleanLicense}");
        }

        // Update driver profile fields
        driver.LicenseNumber = cleanLicense;
        driver.LicenseExpiryDate = request.LicenseExpiryDate;
        driver.LicenseIssueDate = request.LicenseIssueDate;
        if (!string.IsNullOrWhiteSpace(request.LicenseIssuingAuthority))
            driver.LicenseIssuingAuthority = request.LicenseIssuingAuthority.Trim();
        if (!string.IsNullOrWhiteSpace(request.LicenseIssuingState))
            driver.LicenseIssuingState = request.LicenseIssuingState.Trim();
        if (!string.IsNullOrWhiteSpace(request.LicenseIssuingCountry))
            driver.LicenseIssuingCountry = request.LicenseIssuingCountry.Trim();
        if (!string.IsNullOrWhiteSpace(request.LicenseClass))
            driver.LicenseClass = request.LicenseClass.Trim();

        driver.DateOfBirth = request.DateOfBirth;
        driver.ResidentialAddress = request.ResidentialAddress.Trim();
        driver.City = request.City.Trim();
        driver.State = request.State.Trim();
        driver.PostalCode = request.PostalCode.Trim();

        if (!string.IsNullOrWhiteSpace(request.Country))
            driver.Country = request.Country.Trim();
        if (!string.IsNullOrWhiteSpace(request.PreferredLanguage))
            driver.PreferredLanguage = request.PreferredLanguage.Trim();

        driver.UpdatedAt = DateTime.UtcNow;

        if (request.EmergencyContact != null)
        {
            await SaveEmergencyContactAsync(user, request.EmergencyContact);
        }

        await _onboardingPolicyService.EvaluateAndSyncDriverOnboardingStatusAsync(driver);
        await _dbContext.SaveChangesAsync();

        return await MapToResponseAsync(driver);
    }

    public async Task<DriverProfileResponse> PatchDriverProfileAsync(string userUuid, PatchDriverProfileRequest request)
    {
        var user = await GetUserOrThrowAsync(userUuid);
        var driver = await GetOrCreateDriverProfileAsync(user);

        if (!string.IsNullOrWhiteSpace(request.LicenseNumber))
        {
            var cleanLicense = request.LicenseNumber.Trim().ToUpperInvariant();
            var exists = await _dbContext.DriverProfiles.AnyAsync(p => p.LicenseNumber == cleanLicense && p.Id != driver.Id);
            if (exists)
            {
                throw new DuplicateResourceException($"Driving license number is already registered: {cleanLicense}");
            }
            driver.LicenseNumber = cleanLicense;
        }

        if (request.LicenseExpiryDate != null)
        {
            driver.LicenseExpiryDate = request.LicenseExpiryDate;
        }
        if (request.LicenseIssueDate != null)
        {
            driver.LicenseIssueDate = request.LicenseIssueDate;
        }

        if (driver.LicenseIssueDate != null && driver.LicenseExpiryDate != null)
        {
            if (driver.LicenseExpiryDate <= driver.LicenseIssueDate)
            {
                throw new BadRequestException("Driving license expiry date must be later than the issue date");
            }
        }

        if (!string.IsNullOrWhiteSpace(request.LicenseIssuingAuthority))
            driver.LicenseIssuingAuthority = request.LicenseIssuingAuthority.Trim();
        if (!string.IsNullOrWhiteSpace(request.LicenseIssuingState))
            driver.LicenseIssuingState = request.LicenseIssuingState.Trim();
        if (!string.IsNullOrWhiteSpace(request.LicenseIssuingCountry))
            driver.LicenseIssuingCountry = request.LicenseIssuingCountry.Trim();
        if (!string.IsNullOrWhiteSpace(request.LicenseClass))
            driver.LicenseClass = request.LicenseClass.Trim();

        if (request.DateOfBirth != null)
        {
            var today = DateOnly.FromDateTime(DateTime.UtcNow);
            var age = GetAge(request.DateOfBirth.Value, today);
            if (age < 18)
            {
                throw new BadRequestException("Driver must be at least 18 years of age");
            }
            driver.DateOfBirth = request.DateOfBirth;
        }

        if (!string.IsNullOrWhiteSpace(request.ResidentialAddress))
            driver.ResidentialAddress = request.ResidentialAddress.Trim();
        if (!string.IsNullOrWhiteSpace(request.City))
            driver.City = request.City.Trim();
        if (!string.IsNullOrWhiteSpace(request.State))
            driver.State = request.State.Trim();
        if (!string.IsNullOrWhiteSpace(request.PostalCode))
            driver.PostalCode = request.PostalCode.Trim();
        if (!string.IsNullOrWhiteSpace(request.Country))
            driver.Country = request.Country.Trim();
        if (!string.IsNullOrWhiteSpace(request.PreferredLanguage))
            driver.PreferredLanguage = request.PreferredLanguage.Trim();

        driver.UpdatedAt = DateTime.UtcNow;

        if (request.EmergencyContact != null)
        {
            await SaveEmergencyContactAsync(user, request.EmergencyContact);
        }

        await _onboardingPolicyService.EvaluateAndSyncDriverOnboardingStatusAsync(driver);
        await _dbContext.SaveChangesAsync();

        return await MapToResponseAsync(driver);
    }

    public async Task<DriverOnboardingResponse> GetOnboardingStatusAsync(string userUuid)
    {
        var user = await GetUserOrThrowAsync(userUuid);
        var driver = await GetOrCreateDriverProfileAsync(user);
        return await _onboardingPolicyService.CalculateOnboardingProgressAsync(driver);
    }

    public async Task<DriverOnboardingResponse> SubmitForReviewAsync(string userUuid)
    {
        var user = await GetUserOrThrowAsync(userUuid);
        var driver = await GetOrCreateDriverProfileAsync(user);

        var progress = await _onboardingPolicyService.CalculateOnboardingProgressAsync(driver);
        if (!progress.CanSubmitForReview)
        {
            throw new BadRequestException($"Cannot submit profile for review: prerequisites not satisfied: {string.Join("; ", progress.OutstandingTasks)}");
        }

        var previousStatus = driver.OnboardingStatus.ToString();
        driver.OnboardingStatus = OnboardingStatus.PENDING_REVIEW;
        driver.RejectionReason = null;
        driver.UpdatedAt = DateTime.UtcNow;

        _dbContext.DriverVerificationHistories.Add(new DriverVerificationHistory
        {
            DriverId = driver.Id,
            DocumentId = null,
            ActionType = "PROFILE_SUBMISSION",
            PreviousStatus = previousStatus,
            NewStatus = OnboardingStatus.PENDING_REVIEW.ToString(),
            ReviewedByAdminId = user.Id,
            Reason = "Driver submitted completed profile and required documents for review",
            CreatedAt = DateTime.UtcNow
        });

        await _dbContext.SaveChangesAsync();

        return await _onboardingPolicyService.CalculateOnboardingProgressAsync(driver);
    }

    public async Task<DriverAvailabilityResponse> UpdateAvailabilityAsync(string userUuid, DriverAvailabilityRequest request)
    {
        var user = await GetUserOrThrowAsync(userUuid);
        var driver = await GetOrCreateDriverProfileAsync(user);

        if (user.Status != UserStatus.ACTIVE)
        {
            throw new ForbiddenException($"Account is not active ({user.Status}). Cannot change availability.");
        }

        bool requestedOnline;
        if (request.IsOnline.HasValue)
        {
            requestedOnline = request.IsOnline.Value;
        }
        else if (request.Status.HasValue)
        {
            requestedOnline = request.Status.Value == DriverStatus.ONLINE;
        }
        else
        {
            throw new BadRequestException("Must specify desired status or isOnline flag");
        }

        if (requestedOnline)
        {
            if (driver.OnboardingStatus != OnboardingStatus.VERIFIED || !driver.IsVerified)
            {
                throw new ForbiddenException("Driver cannot go online: Onboarding is not fully VERIFIED.");
            }
            if (driver.Status == DriverStatus.SUSPENDED)
            {
                throw new ForbiddenException("Driver is SUSPENDED and cannot go online.");
            }
            driver.Status = DriverStatus.ONLINE;
        }
        else
        {
            driver.Status = DriverStatus.OFFLINE;
        }

        driver.UpdatedAt = DateTime.UtcNow;
        await _dbContext.SaveChangesAsync();

        return new DriverAvailabilityResponse
        {
            DriverUuid = user.Uuid,
            Status = driver.Status,
            IsOnline = driver.Status == DriverStatus.ONLINE,
            Message = $"Driver availability updated to {driver.Status}"
        };
    }

    public async Task<DriverProfile> GetOrCreateDriverProfileAsync(User user)
    {
        var driver = await _dbContext.DriverProfiles
            .Include(p => p.User)
            .FirstOrDefaultAsync(p => p.UserId == user.Id);

        if (driver != null) return driver;

        driver = new DriverProfile
        {
            UserId = user.Id,
            User = user,
            Status = DriverStatus.OFFLINE,
            Rating = 5.00m,
            TotalTrips = 0,
            AcceptanceRate = 100.00m,
            CancellationRate = 0.00m,
            IsVerified = false,
            OnboardingStatus = OnboardingStatus.INCOMPLETE,
            Country = "India",
            LicenseIssuingCountry = "India",
            LicenseClass = "LMV",
            PreferredLanguage = "English",
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        _dbContext.DriverProfiles.Add(driver);
        await _dbContext.SaveChangesAsync();
        return driver;
    }

    public async Task<DriverProfileResponse> MapToResponseAsync(DriverProfile driver)
    {
        var user = driver.User ?? await _dbContext.Users.FirstOrDefaultAsync(u => u.Id == driver.UserId);
        EmergencyContactDto? emergencyContactDto = null;
        if (user != null)
        {
            var ec = await _dbContext.EmergencyContacts
                .Where(e => e.UserId == user.Id)
                .OrderByDescending(e => e.CreatedAt)
                .FirstOrDefaultAsync();

            if (ec != null)
            {
                emergencyContactDto = new EmergencyContactDto
                {
                    ContactName = ec.ContactName,
                    ContactPhone = ec.ContactPhone,
                    Relationship = ec.Relationship
                };
            }
        }

        return new DriverProfileResponse
        {
            DriverId = driver.Id,
            UserUuid = user?.Uuid,
            FirstName = user?.FirstName,
            LastName = user?.LastName,
            Email = user?.Email,
            PhoneNumber = user?.PhoneNumber,
            ProfilePictureUrl = user?.ProfilePictureUrl,
            LicenseNumber = driver.LicenseNumber,
            LicenseExpiryDate = driver.LicenseExpiryDate,
            LicenseIssueDate = driver.LicenseIssueDate,
            LicenseIssuingAuthority = driver.LicenseIssuingAuthority,
            LicenseIssuingState = driver.LicenseIssuingState,
            LicenseIssuingCountry = driver.LicenseIssuingCountry,
            LicenseClass = driver.LicenseClass,
            DateOfBirth = driver.DateOfBirth,
            ResidentialAddress = driver.ResidentialAddress,
            City = driver.City,
            State = driver.State,
            PostalCode = driver.PostalCode,
            Country = driver.Country,
            PreferredLanguage = driver.PreferredLanguage,
            Status = driver.Status,
            Rating = driver.Rating,
            TotalTrips = driver.TotalTrips,
            AcceptanceRate = driver.AcceptanceRate,
            CancellationRate = driver.CancellationRate,
            IsVerified = driver.IsVerified,
            VerifiedAt = driver.VerifiedAt,
            BackgroundCheckStatus = driver.BackgroundCheckStatus,
            OnboardingStatus = driver.OnboardingStatus,
            RejectionReason = driver.RejectionReason,
            EmergencyContact = emergencyContactDto,
            CreatedAt = driver.CreatedAt,
            UpdatedAt = driver.UpdatedAt
        };
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

    private async Task SaveEmergencyContactAsync(User user, EmergencyContactDto dto)
    {
        var contact = await _dbContext.EmergencyContacts
            .Where(e => e.UserId == user.Id)
            .OrderByDescending(e => e.CreatedAt)
            .FirstOrDefaultAsync();

        if (contact == null)
        {
            contact = new EmergencyContact
            {
                UserId = user.Id,
                CreatedAt = DateTime.UtcNow
            };
            _dbContext.EmergencyContacts.Add(contact);
        }

        contact.ContactName = dto.ContactName.Trim();
        contact.ContactPhone = dto.ContactPhone.Trim();
        contact.Relationship = string.IsNullOrWhiteSpace(dto.Relationship) ? null : dto.Relationship.Trim();
    }

    private static int GetAge(DateOnly dateOfBirth, DateOnly today)
    {
        var age = today.Year - dateOfBirth.Year;
        if (dateOfBirth > today.AddYears(-age)) age--;
        return age;
    }
}
