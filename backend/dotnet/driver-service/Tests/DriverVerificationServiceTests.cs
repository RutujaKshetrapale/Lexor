using DriverService.Configuration;
using DriverService.DTOs.Request;
using DriverService.Entities;
using DriverService.Enums;
using DriverService.Exceptions;
using DriverService.Services;
using DriverService.Storage;
using Microsoft.Extensions.Options;
using Moq;
using Xunit;

namespace DriverService.Tests;

public class DriverVerificationServiceTests
{
    private static IOptions<OnboardingPolicySettings> CreatePolicySettings()
    {
        return Options.Create(new OnboardingPolicySettings
        {
            RequiredDocuments = "DRIVING_LICENSE,GOVERNMENT_ID,ADDRESS_PROOF"
        });
    }

    [Fact]
    public async Task ReviewDocument_Approve_SetsStatusToVerified()
    {
        using var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var mockStorage = new Mock<IDocumentStorageService>();
        var policyService = new OnboardingPolicyService(db, CreatePolicySettings());
        var profileService = new DriverProfileService(db, policyService);
        var verificationService = new DriverVerificationService(db, mockStorage.Object, profileService, policyService);

        var admin = new User { Id = 10, Uuid = "admin-uuid", FirstName = "Admin", LastName = "User", Role = UserRole.ADMIN };
        var driverUser = new User { Id = 1, Uuid = "driver-uuid", FirstName = "Vikram", LastName = "Singh", Role = UserRole.DRIVER };
        db.Users.AddRange(admin, driverUser);

        var driver = new DriverProfile { Id = 1, UserId = 1, User = driverUser, OnboardingStatus = OnboardingStatus.PENDING_REVIEW };
        db.DriverProfiles.Add(driver);

        var doc = new DriverDocument
        {
            Id = 100,
            DriverId = 1,
            DocumentType = DocumentType.DRIVING_LICENSE,
            FileName = "license.pdf",
            FilePath = "license.pdf",
            ContentType = "application/pdf",
            FileSize = 1024,
            IsCurrent = true,
            VerificationStatus = DocumentVerificationStatus.PENDING_REVIEW
        };
        db.DriverDocuments.Add(doc);
        await db.SaveChangesAsync();

        var request = new ReviewDocumentRequest
        {
            Status = DocumentVerificationStatus.VERIFIED,
            ReviewNotes = "Valid government driving license verified"
        };

        var response = await verificationService.ReviewDocumentAsync("admin-uuid", 100, request);

        Assert.NotNull(response);
        Assert.Equal(DocumentVerificationStatus.VERIFIED, response.VerificationStatus);
        Assert.Equal(10, response.ReviewedByAdminId);

        // Audit check
        var history = await verificationService.GetDriverVerificationHistoryAsync("driver-uuid");
        Assert.NotEmpty(history);
        Assert.Contains(history, h => h.ActionType == "DOCUMENT_REVIEW" && h.NewStatus == "VERIFIED");
    }

    [Fact]
    public async Task ReviewDocument_RejectWithoutReason_ThrowsBadRequest()
    {
        using var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var mockStorage = new Mock<IDocumentStorageService>();
        var policyService = new OnboardingPolicyService(db, CreatePolicySettings());
        var profileService = new DriverProfileService(db, policyService);
        var verificationService = new DriverVerificationService(db, mockStorage.Object, profileService, policyService);

        var admin = new User { Id = 10, Uuid = "admin-uuid", FirstName = "Admin", LastName = "User" };
        var driverUser = new User { Id = 1, Uuid = "driver-uuid", FirstName = "Vikram", LastName = "Singh" };
        db.Users.AddRange(admin, driverUser);

        var driver = new DriverProfile { Id = 1, UserId = 1, User = driverUser };
        db.DriverProfiles.Add(driver);

        var doc = new DriverDocument
        {
            Id = 100,
            DriverId = 1,
            DocumentType = DocumentType.DRIVING_LICENSE,
            FileName = "license.pdf",
            FilePath = "license.pdf",
            ContentType = "application/pdf",
            FileSize = 1024,
            IsCurrent = true,
            VerificationStatus = DocumentVerificationStatus.PENDING_REVIEW
        };
        db.DriverDocuments.Add(doc);
        await db.SaveChangesAsync();

        var request = new ReviewDocumentRequest
        {
            Status = DocumentVerificationStatus.REJECTED,
            RejectionReason = null,
            ReviewNotes = null
        };

        await Assert.ThrowsAsync<BadRequestException>(() => verificationService.ReviewDocumentAsync("admin-uuid", 100, request));
    }

    [Fact]
    public async Task ReviewDriverVerification_ApproveWhenIncomplete_ThrowsBadRequest()
    {
        using var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var mockStorage = new Mock<IDocumentStorageService>();
        var policyService = new OnboardingPolicyService(db, CreatePolicySettings());
        var profileService = new DriverProfileService(db, policyService);
        var verificationService = new DriverVerificationService(db, mockStorage.Object, profileService, policyService);

        var admin = new User { Id = 10, Uuid = "admin-uuid", FirstName = "Admin", LastName = "User" };
        var driverUser = new User { Id = 1, Uuid = "driver-uuid", FirstName = "Vikram", LastName = "Singh" };
        db.Users.AddRange(admin, driverUser);

        // Driver with missing address and documents
        var driver = new DriverProfile
        {
            Id = 1,
            UserId = 1,
            User = driverUser,
            OnboardingStatus = OnboardingStatus.PENDING_REVIEW,
            IsVerified = false
        };
        db.DriverProfiles.Add(driver);
        await db.SaveChangesAsync();

        var request = new VerifyDriverRequest
        {
            Status = OnboardingStatus.VERIFIED
        };

        await Assert.ThrowsAsync<BadRequestException>(() => verificationService.ReviewDriverVerificationAsync("admin-uuid", "driver-uuid", request));
    }

    [Fact]
    public async Task UpdateDriverSuspension_SuspendAndReinstate_UpdatesStatusCorrectly()
    {
        using var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var mockStorage = new Mock<IDocumentStorageService>();
        var policyService = new OnboardingPolicyService(db, CreatePolicySettings());
        var profileService = new DriverProfileService(db, policyService);
        var verificationService = new DriverVerificationService(db, mockStorage.Object, profileService, policyService);

        var admin = new User { Id = 10, Uuid = "admin-uuid", FirstName = "Admin", LastName = "User" };
        var driverUser = new User { Id = 1, Uuid = "driver-uuid", FirstName = "Vikram", LastName = "Singh", Status = UserStatus.ACTIVE };
        db.Users.AddRange(admin, driverUser);

        var driver = new DriverProfile
        {
            Id = 1,
            UserId = 1,
            User = driverUser,
            Status = DriverStatus.ONLINE,
            OnboardingStatus = OnboardingStatus.VERIFIED,
            IsVerified = true
        };
        db.DriverProfiles.Add(driver);
        await db.SaveChangesAsync();

        // Suspend
        var suspendReq = new DriverSuspensionRequest { Suspended = true, Reason = "Policy violation investigation" };
        var suspendRes = await verificationService.UpdateDriverSuspensionAsync("admin-uuid", "driver-uuid", suspendReq);

        Assert.Equal(DriverStatus.SUSPENDED, suspendRes.Status);
        Assert.Equal(OnboardingStatus.SUSPENDED, suspendRes.OnboardingStatus);

        // Reinstate
        var reinstateReq = new DriverSuspensionRequest { Suspended = false, Reason = "Investigation concluded with no fault" };
        var reinstateRes = await verificationService.UpdateDriverSuspensionAsync("admin-uuid", "driver-uuid", reinstateReq);

        Assert.Equal(DriverStatus.OFFLINE, reinstateRes.Status);
    }
}
