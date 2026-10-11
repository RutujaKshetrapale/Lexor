using DriverService.Configuration;
using DriverService.Entities;
using DriverService.Enums;
using DriverService.Services;
using Microsoft.Extensions.Options;
using Xunit;

namespace DriverService.Tests;

public class OnboardingPolicyServiceTests
{
    private static IOptions<OnboardingPolicySettings> CreatePolicySettings()
    {
        return Options.Create(new OnboardingPolicySettings
        {
            RequiredDocuments = "DRIVING_LICENSE,GOVERNMENT_ID,ADDRESS_PROOF"
        });
    }

    [Fact]
    public async Task IsProfileComplete_IncompleteWhenLicenseMissing_ReturnsFalse()
    {
        using var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var service = new OnboardingPolicyService(db, CreatePolicySettings());

        var user = new User { Id = 1, Uuid = "u-1", FirstName = "John", LastName = "Doe" };
        var driver = new DriverProfile { Id = 1, UserId = 1, User = user };

        var result = await service.IsProfileCompleteAsync(driver);
        Assert.False(result);
    }

    [Fact]
    public async Task IsProfileComplete_IncompleteWhenUnder18_ReturnsFalse()
    {
        using var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var service = new OnboardingPolicyService(db, CreatePolicySettings());

        var user = new User { Id = 1, Uuid = "u-1", FirstName = "Young", LastName = "Driver" };
        db.Users.Add(user);
        db.EmergencyContacts.Add(new EmergencyContact { UserId = 1, ContactName = "Mom", ContactPhone = "+919876543210" });
        await db.SaveChangesAsync();

        var driver = new DriverProfile
        {
            Id = 1,
            UserId = 1,
            User = user,
            LicenseNumber = "DL12345",
            LicenseExpiryDate = DateOnly.FromDateTime(DateTime.UtcNow.AddYears(2)),
            DateOfBirth = DateOnly.FromDateTime(DateTime.UtcNow.AddYears(-17)), // 17 years old
            ResidentialAddress = "123 Main St",
            City = "Mumbai",
            State = "Maharashtra",
            PostalCode = "400001"
        };

        var result = await service.IsProfileCompleteAsync(driver);
        Assert.False(result);
    }

    [Fact]
    public async Task IsProfileComplete_CompleteProfile_ReturnsTrue()
    {
        using var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var service = new OnboardingPolicyService(db, CreatePolicySettings());

        var user = new User { Id = 1, Uuid = "u-1", FirstName = "John", LastName = "Doe" };
        db.Users.Add(user);
        db.EmergencyContacts.Add(new EmergencyContact { UserId = 1, ContactName = "Mom", ContactPhone = "+919876543210" });
        await db.SaveChangesAsync();

        var driver = new DriverProfile
        {
            Id = 1,
            UserId = 1,
            User = user,
            LicenseNumber = "DL12345",
            LicenseExpiryDate = DateOnly.FromDateTime(DateTime.UtcNow.AddYears(3)),
            DateOfBirth = DateOnly.FromDateTime(DateTime.UtcNow.AddYears(-25)),
            ResidentialAddress = "123 Main St",
            City = "Mumbai",
            State = "Maharashtra",
            PostalCode = "400001"
        };

        var result = await service.IsProfileCompleteAsync(driver);
        Assert.True(result);
    }

    [Fact]
    public async Task CalculateOnboardingProgress_WithMissingDocuments_IdentifiesOutstandingTasks()
    {
        using var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var service = new OnboardingPolicyService(db, CreatePolicySettings());

        var user = new User { Id = 1, Uuid = "u-1", FirstName = "John", LastName = "Doe" };
        db.Users.Add(user);
        await db.SaveChangesAsync();

        var driver = new DriverProfile
        {
            Id = 1,
            UserId = 1,
            User = user,
            OnboardingStatus = OnboardingStatus.INCOMPLETE
        };

        var progress = await service.CalculateOnboardingProgressAsync(driver);

        Assert.False(progress.ProfileComplete);
        Assert.False(progress.DocumentsComplete);
        Assert.False(progress.CanSubmitForReview);
        Assert.Contains(progress.OutstandingTasks, t => t.Contains("Upload required document: DRIVING LICENSE"));
        Assert.Contains(progress.OutstandingTasks, t => t.Contains("Upload required document: GOVERNMENT ID"));
        Assert.Contains(progress.OutstandingTasks, t => t.Contains("Upload required document: ADDRESS PROOF"));
    }

    [Fact]
    public async Task EvaluateAndSyncDriverOnboardingStatus_WhenDocumentsRejected_SetsStatusToRejected()
    {
        using var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var service = new OnboardingPolicyService(db, CreatePolicySettings());

        var user = new User { Id = 1, Uuid = "u-1", FirstName = "John", LastName = "Doe" };
        db.Users.Add(user);

        var driver = new DriverProfile
        {
            Id = 1,
            UserId = 1,
            User = user,
            OnboardingStatus = OnboardingStatus.PENDING_REVIEW,
            IsVerified = false
        };
        db.DriverProfiles.Add(driver);

        db.DriverDocuments.Add(new DriverDocument
        {
            Id = 1,
            DriverId = 1,
            DocumentType = DocumentType.DRIVING_LICENSE,
            IsCurrent = true,
            VerificationStatus = DocumentVerificationStatus.REJECTED,
            RejectionReason = "Expired licence photocopy"
        });
        await db.SaveChangesAsync();

        await service.EvaluateAndSyncDriverOnboardingStatusAsync(driver);

        Assert.Equal(OnboardingStatus.REJECTED, driver.OnboardingStatus);
        Assert.False(driver.IsVerified);
    }
}
