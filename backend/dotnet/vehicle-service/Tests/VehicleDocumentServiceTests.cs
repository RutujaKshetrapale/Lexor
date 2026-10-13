using System.Security.Claims;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging;
using Moq;
using VehicleService.Data;
using VehicleService.Entities;
using VehicleService.Enums;
using VehicleService.Exceptions;
using VehicleService.Services;
using VehicleService.Storage;
using Xunit;

namespace VehicleService.Tests;

public class VehicleDocumentServiceTests
{
    private static (LexorDbContext db, VehicleDocumentService service, Vehicle vehicle, ClaimsPrincipal driverPrincipal) CreateContextAndService()
    {
        var db = TestDbContextFactory.Create("VehicleDocumentServiceTests_" + Guid.NewGuid().ToString("N"));

        var user = new User
        {
            Id = 1,
            Uuid = "driver-uuid-1",
            FullName = "Vikram Singh",
            Email = "driver1@lexor.com",
            PasswordHash = "hash",
            Role = Role.DRIVER,
            Status = UserStatus.ACTIVE
        };
        db.Users.Add(user);

        var driver = new DriverProfile
        {
            Id = 1,
            UserId = 1,
            User = user,
            LicenseNumber = "DL-12345",
            IsVerified = true,
            OnboardingStatus = OnboardingStatus.VERIFIED
        };
        db.DriverProfiles.Add(driver);

        var vehicle = new Vehicle
        {
            Id = 1,
            Uuid = "vehicle-uuid-1",
            DriverId = 1,
            Driver = driver,
            Make = "Toyota",
            Model = "Hyryder",
            Year = 2023,
            Color = "Black",
            LicensePlate = "KA-01-EQ-4829",
            RideTypeCategory = "SEDAN",
            VerificationStatus = VehicleVerificationStatus.PENDING_VERIFICATION
        };
        db.Vehicles.Add(vehicle);
        db.SaveChanges();

        var mockStorage = new Mock<IDocumentStorageService>();
        mockStorage.Setup(s => s.StoreAsync(It.IsAny<IFormFile>()))
            .ReturnsAsync(new StoredDocument
            {
                OriginalFileName = "rc.pdf",
                StoredFileName = "uuid-stored.pdf",
                StoragePath = "uploads/vehicle-documents/uuid-stored.pdf",
                ContentType = "application/pdf",
                FileSize = 1024
            });
        mockStorage.Setup(s => s.LoadAsStream(It.IsAny<string>()))
            .Returns(new MemoryStream(new byte[] { 0x25, 0x50, 0x44, 0x46 }));

        var logger = new Mock<ILogger<VehicleDocumentService>>();
        var service = new VehicleDocumentService(db, mockStorage.Object, logger.Object);

        var driverPrincipal = new ClaimsPrincipal(new ClaimsIdentity(new[]
        {
            new Claim("sub", "driver-uuid-1"),
            new Claim("userId", "1"),
            new Claim(ClaimTypes.Role, "ROLE_DRIVER"),
            new Claim("role", "ROLE_DRIVER")
        }, "TestAuth"));

        return (db, service, vehicle, driverPrincipal);
    }

    [Fact]
    public async Task UploadDocumentAsync_NewDocument_CreatesVersion1()
    {
        var (db, service, vehicle, driverPrincipal) = CreateContextAndService();

        var fileMock = new Mock<IFormFile>();
        var response = await service.UploadDocumentAsync(
            driverPrincipal,
            vehicle.Uuid,
            VehicleDocumentType.REGISTRATION_CERTIFICATE,
            "RC-12345",
            new DateOnly(2023, 1, 1),
            new DateOnly(2038, 1, 1),
            "RTO Bangalore",
            fileMock.Object
        );

        Assert.NotNull(response);
        Assert.Equal(1, response.Version);
        Assert.True(response.IsCurrent);
        Assert.Equal(DocumentVerificationStatus.PENDING_REVIEW, response.VerificationStatus);
        Assert.Equal(VehicleDocumentType.REGISTRATION_CERTIFICATE, response.DocumentType);
    }

    [Fact]
    public async Task UploadDocumentAsync_SameDocumentType_ArchivesOldAndIncrementsVersion()
    {
        var (db, service, vehicle, driverPrincipal) = CreateContextAndService();

        var fileMock = new Mock<IFormFile>();

        // Upload version 1
        var v1 = await service.UploadDocumentAsync(
            driverPrincipal,
            vehicle.Uuid,
            VehicleDocumentType.VEHICLE_INSURANCE,
            "POL-001",
            null, null, null,
            fileMock.Object
        );

        // Upload replacement (version 2)
        var v2 = await service.UploadDocumentAsync(
            driverPrincipal,
            vehicle.Uuid,
            VehicleDocumentType.VEHICLE_INSURANCE,
            "POL-002",
            null, null, null,
            fileMock.Object
        );

        Assert.Equal(2, v2.Version);
        Assert.True(v2.IsCurrent);

        var oldDoc = db.VehicleDocuments.First(d => d.Id == v1.Id);
        Assert.False(oldDoc.IsCurrent);
    }

    [Fact]
    public async Task ResubmitDocumentAsync_AlreadyVerified_ThrowsBadRequestException()
    {
        var (db, service, vehicle, driverPrincipal) = CreateContextAndService();

        var doc = new VehicleDocument
        {
            VehicleId = vehicle.Id,
            DocumentType = VehicleDocumentType.REGISTRATION_CERTIFICATE,
            FileName = "rc.pdf",
            FilePath = "uploads/rc.pdf",
            ContentType = "application/pdf",
            FileSize = 1000,
            VerificationStatus = DocumentVerificationStatus.VERIFIED, // Already verified!
            IsCurrent = true,
            Version = 1
        };
        db.VehicleDocuments.Add(doc);
        db.SaveChanges();

        var fileMock = new Mock<IFormFile>();

        await Assert.ThrowsAsync<BadRequestException>(() =>
            service.ResubmitDocumentAsync(
                driverPrincipal,
                vehicle.Uuid,
                doc.Id,
                null, null, null, null,
                fileMock.Object
            ));
    }

    [Fact]
    public async Task DeleteDocumentAsync_Unverified_SoftDeletesDocument()
    {
        var (db, service, vehicle, driverPrincipal) = CreateContextAndService();

        var doc = new VehicleDocument
        {
            VehicleId = vehicle.Id,
            DocumentType = VehicleDocumentType.POLLUTION_UNDER_CONTROL,
            FileName = "puc.pdf",
            FilePath = "uploads/puc.pdf",
            ContentType = "application/pdf",
            FileSize = 1000,
            VerificationStatus = DocumentVerificationStatus.PENDING_REVIEW,
            IsCurrent = true,
            Version = 1
        };
        db.VehicleDocuments.Add(doc);
        db.SaveChanges();

        await service.DeleteDocumentAsync(driverPrincipal, vehicle.Uuid, doc.Id);

        var updatedDoc = db.VehicleDocuments.First(d => d.Id == doc.Id);
        Assert.False(updatedDoc.IsCurrent);
    }

    [Fact]
    public async Task DeleteDocumentAsync_Verified_ThrowsBadRequestException()
    {
        var (db, service, vehicle, driverPrincipal) = CreateContextAndService();

        var doc = new VehicleDocument
        {
            VehicleId = vehicle.Id,
            DocumentType = VehicleDocumentType.POLLUTION_UNDER_CONTROL,
            FileName = "puc.pdf",
            FilePath = "uploads/puc.pdf",
            ContentType = "application/pdf",
            FileSize = 1000,
            VerificationStatus = DocumentVerificationStatus.VERIFIED,
            IsCurrent = true,
            Version = 1
        };
        db.VehicleDocuments.Add(doc);
        db.SaveChanges();

        await Assert.ThrowsAsync<BadRequestException>(() =>
            service.DeleteDocumentAsync(driverPrincipal, vehicle.Uuid, doc.Id));
    }
}
