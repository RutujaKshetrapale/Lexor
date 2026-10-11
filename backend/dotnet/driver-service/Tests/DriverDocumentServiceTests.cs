using DriverService.Configuration;
using DriverService.Entities;
using DriverService.Enums;
using DriverService.Exceptions;
using DriverService.Services;
using DriverService.Storage;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Options;
using Moq;
using Xunit;

namespace DriverService.Tests;

public class DriverDocumentServiceTests
{
    private static IOptions<OnboardingPolicySettings> CreatePolicySettings()
    {
        return Options.Create(new OnboardingPolicySettings
        {
            RequiredDocuments = "DRIVING_LICENSE,GOVERNMENT_ID,ADDRESS_PROOF"
        });
    }

    private static IFormFile CreateMockPdfFile()
    {
        var content = "%PDF-1.4 test file"u8.ToArray();
        var stream = new MemoryStream(content);
        return new FormFile(stream, 0, content.Length, "file", "license.pdf")
        {
            Headers = new HeaderDictionary(),
            ContentType = "application/pdf"
        };
    }

    [Fact]
    public async Task UploadDocument_FirstUpload_CreatesVersion1()
    {
        using var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var mockStorage = new Mock<IDocumentStorageService>();
        mockStorage.Setup(s => s.StoreAsync(It.IsAny<IFormFile>()))
            .ReturnsAsync(new StoredDocument
            {
                OriginalFileName = "license.pdf",
                StoredFileName = "stored-uuid.pdf",
                StoragePath = "uploads/documents/stored-uuid.pdf",
                ContentType = "application/pdf",
                FileSize = 1024
            });

        var policyService = new OnboardingPolicyService(db, CreatePolicySettings());
        var profileService = new DriverProfileService(db, policyService);
        var documentService = new DriverDocumentService(db, mockStorage.Object, policyService, profileService);

        var user = new User { Id = 1, Uuid = "u-uuid-1", FirstName = "Vikram", LastName = "Singh", Status = UserStatus.ACTIVE };
        db.Users.Add(user);
        await db.SaveChangesAsync();

        var response = await documentService.UploadDocumentAsync(
            "u-uuid-1",
            DocumentType.DRIVING_LICENSE,
            "DL-12345",
            DateOnly.FromDateTime(DateTime.UtcNow.AddYears(-2)),
            DateOnly.FromDateTime(DateTime.UtcNow.AddYears(3)),
            "RTO Mumbai",
            CreateMockPdfFile()
        );

        Assert.NotNull(response);
        Assert.Equal(1, response.Version);
        Assert.True(response.IsCurrent);
        Assert.Equal(DocumentVerificationStatus.PENDING_REVIEW, response.VerificationStatus);
    }

    [Fact]
    public async Task UploadDocument_Replacement_IncrementsVersionAndSupersedesPrevious()
    {
        using var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var mockStorage = new Mock<IDocumentStorageService>();
        mockStorage.Setup(s => s.StoreAsync(It.IsAny<IFormFile>()))
            .ReturnsAsync(new StoredDocument
            {
                OriginalFileName = "license_v2.pdf",
                StoredFileName = "stored-uuid-v2.pdf",
                StoragePath = "uploads/documents/stored-uuid-v2.pdf",
                ContentType = "application/pdf",
                FileSize = 1024
            });

        var policyService = new OnboardingPolicyService(db, CreatePolicySettings());
        var profileService = new DriverProfileService(db, policyService);
        var documentService = new DriverDocumentService(db, mockStorage.Object, policyService, profileService);

        var user = new User { Id = 1, Uuid = "u-uuid-1", FirstName = "Vikram", LastName = "Singh", Status = UserStatus.ACTIVE };
        db.Users.Add(user);
        var driver = new DriverProfile { Id = 1, UserId = 1, User = user };
        db.DriverProfiles.Add(driver);

        // Add version 1
        var docV1 = new DriverDocument
        {
            Id = 1,
            DriverId = 1,
            DocumentType = DocumentType.DRIVING_LICENSE,
            FileName = "license_v1.pdf",
            FilePath = "path1.pdf",
            ContentType = "application/pdf",
            FileSize = 500,
            Version = 1,
            IsCurrent = true,
            VerificationStatus = DocumentVerificationStatus.REJECTED
        };
        db.DriverDocuments.Add(docV1);
        await db.SaveChangesAsync();

        var response = await documentService.UploadDocumentAsync(
            "u-uuid-1",
            DocumentType.DRIVING_LICENSE,
            "DL-12345-NEW",
            null, null, null,
            CreateMockPdfFile()
        );

        Assert.Equal(2, response.Version);
        Assert.True(response.IsCurrent);
        Assert.Equal(DocumentVerificationStatus.PENDING_REVIEW, response.VerificationStatus);

        // Verify version 1 is marked false
        var updatedV1 = await db.DriverDocuments.FindAsync(1L);
        Assert.False(updatedV1!.IsCurrent);
    }

    [Fact]
    public async Task DownloadDocument_NonOwnerDriver_ThrowsForbidden()
    {
        using var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var mockStorage = new Mock<IDocumentStorageService>();
        var policyService = new OnboardingPolicyService(db, CreatePolicySettings());
        var profileService = new DriverProfileService(db, policyService);
        var documentService = new DriverDocumentService(db, mockStorage.Object, policyService, profileService);

        var driver1User = new User { Id = 1, Uuid = "driver-1-uuid", FirstName = "Driver", LastName = "One", Status = UserStatus.ACTIVE };
        var driver2User = new User { Id = 2, Uuid = "driver-2-uuid", FirstName = "Driver", LastName = "Two", Status = UserStatus.ACTIVE };
        db.Users.AddRange(driver1User, driver2User);

        var driver1 = new DriverProfile { Id = 1, UserId = 1, User = driver1User };
        var driver2 = new DriverProfile { Id = 2, UserId = 2, User = driver2User };
        db.DriverProfiles.AddRange(driver1, driver2);

        var docOwner1 = new DriverDocument
        {
            Id = 10,
            DriverId = 1, // Owned by Driver 1
            DocumentType = DocumentType.DRIVING_LICENSE,
            FileName = "secret.pdf",
            FilePath = "secret.pdf",
            ContentType = "application/pdf",
            FileSize = 1024,
            IsCurrent = true
        };
        db.DriverDocuments.Add(docOwner1);
        await db.SaveChangesAsync();

        // Driver 2 attempts to download Driver 1's document
        await Assert.ThrowsAsync<ForbiddenException>(() => documentService.DownloadDocumentAsync("driver-2-uuid", 10));
    }

    [Fact]
    public async Task ResubmitDocument_WhenVerified_ThrowsBadRequest()
    {
        using var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var mockStorage = new Mock<IDocumentStorageService>();
        var policyService = new OnboardingPolicyService(db, CreatePolicySettings());
        var profileService = new DriverProfileService(db, policyService);
        var documentService = new DriverDocumentService(db, mockStorage.Object, policyService, profileService);

        var user = new User { Id = 1, Uuid = "driver-uuid", FirstName = "Driver", LastName = "One", Status = UserStatus.ACTIVE };
        db.Users.Add(user);
        var driver = new DriverProfile { Id = 1, UserId = 1, User = user };
        db.DriverProfiles.Add(driver);

        var verifiedDoc = new DriverDocument
        {
            Id = 20,
            DriverId = 1,
            DocumentType = DocumentType.DRIVING_LICENSE,
            FileName = "verified.pdf",
            FilePath = "verified.pdf",
            ContentType = "application/pdf",
            FileSize = 1024,
            IsCurrent = true,
            VerificationStatus = DocumentVerificationStatus.VERIFIED
        };
        db.DriverDocuments.Add(verifiedDoc);
        await db.SaveChangesAsync();

        await Assert.ThrowsAsync<BadRequestException>(() => documentService.ResubmitDocumentAsync(
            "driver-uuid", 20, CreateMockPdfFile(), null, null, null, null
        ));
    }

    [Fact]
    public async Task DeleteDocument_WhenVerified_ThrowsBadRequest()
    {
        using var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var mockStorage = new Mock<IDocumentStorageService>();
        var policyService = new OnboardingPolicyService(db, CreatePolicySettings());
        var profileService = new DriverProfileService(db, policyService);
        var documentService = new DriverDocumentService(db, mockStorage.Object, policyService, profileService);

        var user = new User { Id = 1, Uuid = "driver-uuid", FirstName = "Driver", LastName = "One", Status = UserStatus.ACTIVE };
        db.Users.Add(user);
        var driver = new DriverProfile { Id = 1, UserId = 1, User = user };
        db.DriverProfiles.Add(driver);

        var verifiedDoc = new DriverDocument
        {
            Id = 30,
            DriverId = 1,
            DocumentType = DocumentType.DRIVING_LICENSE,
            FileName = "verified.pdf",
            FilePath = "verified.pdf",
            ContentType = "application/pdf",
            FileSize = 1024,
            IsCurrent = true,
            VerificationStatus = DocumentVerificationStatus.VERIFIED
        };
        db.DriverDocuments.Add(verifiedDoc);
        await db.SaveChangesAsync();

        await Assert.ThrowsAsync<BadRequestException>(() => documentService.DeleteDocumentAsync("driver-uuid", 30));
    }
}
