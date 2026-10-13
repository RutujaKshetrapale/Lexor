using System.Security.Claims;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Moq;
using VehicleService.Configuration;
using VehicleService.Data;
using VehicleService.DTOs.Request;
using VehicleService.Entities;
using VehicleService.Enums;
using VehicleService.Exceptions;
using VehicleService.Services;
using VehicleService.Storage;
using Xunit;

namespace VehicleService.Tests;

public class VehicleAdminServiceTests
{
    private static (LexorDbContext db, VehicleAdminService service, Vehicle vehicle, DriverProfile driver, ClaimsPrincipal adminPrincipal) CreateContextAndService()
    {
        var db = TestDbContextFactory.Create("VehicleAdminServiceTests_" + Guid.NewGuid().ToString("N"));

        var adminUser = new User
        {
            Id = 99,
            Uuid = "admin-uuid-99",
            FullName = "Lexor Administrator",
            Email = "admin@lexor.com",
            PasswordHash = "hash",
            Role = Role.ADMIN,
            Status = UserStatus.ACTIVE
        };
        db.Users.Add(adminUser);

        var driverUser = new User
        {
            Id = 1,
            Uuid = "driver-uuid-1",
            FullName = "Vikram Singh",
            Email = "driver1@lexor.com",
            PasswordHash = "hash",
            Role = Role.DRIVER,
            Status = UserStatus.ACTIVE
        };
        db.Users.Add(driverUser);

        var driver = new DriverProfile
        {
            Id = 1,
            UserId = 1,
            User = driverUser,
            LicenseNumber = "DL-12345",
            IsVerified = true,
            OnboardingStatus = OnboardingStatus.VERIFIED,
            Status = DriverStatus.ONLINE
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
            VerificationStatus = VehicleVerificationStatus.PENDING_VERIFICATION,
            IsActive = true
        };
        db.Vehicles.Add(vehicle);
        db.SaveChanges();

        var mockStorage = new Mock<IDocumentStorageService>();
        var policyOptions = Options.Create(new VehicleVerificationPolicySettings
        {
            RequiredDocuments = "REGISTRATION_CERTIFICATE,VEHICLE_INSURANCE,POLLUTION_UNDER_CONTROL"
        });
        var policyService = new VehicleVerificationPolicyService(policyOptions);
        var logger = new Mock<ILogger<VehicleAdminService>>();

        var service = new VehicleAdminService(db, mockStorage.Object, policyService, logger.Object);

        var adminPrincipal = new ClaimsPrincipal(new ClaimsIdentity(new[]
        {
            new Claim("sub", "admin-uuid-99"),
            new Claim("userId", "99"),
            new Claim(ClaimTypes.Role, "ROLE_ADMIN"),
            new Claim("role", "ROLE_ADMIN")
        }, "TestAuth"));

        return (db, service, vehicle, driver, adminPrincipal);
    }

    [Fact]
    public async Task ListVehiclesAsync_ReturnsPagedResponse()
    {
        var (db, service, vehicle, driver, adminPrincipal) = CreateContextAndService();

        var result = await service.ListVehiclesAsync(null, null, null, null, 0, 10, "createdAt", "desc");

        Assert.NotNull(result);
        Assert.Single(result.Content);
        Assert.Equal(1, result.TotalElements);
        Assert.Equal("KA-01-EQ-4829", result.Content[0].LicensePlate);
    }

    [Fact]
    public async Task ReviewDocumentAsync_UpdatesStatusAndCreatesHistory()
    {
        var (db, service, vehicle, driver, adminPrincipal) = CreateContextAndService();

        var doc = new VehicleDocument
        {
            VehicleId = vehicle.Id,
            DocumentType = VehicleDocumentType.REGISTRATION_CERTIFICATE,
            FileName = "rc.pdf",
            FilePath = "uploads/rc.pdf",
            ContentType = "application/pdf",
            FileSize = 1000,
            VerificationStatus = DocumentVerificationStatus.PENDING_REVIEW,
            IsCurrent = true
        };
        db.VehicleDocuments.Add(doc);
        db.SaveChanges();

        var request = new ReviewVehicleDocumentRequest
        {
            Status = DocumentVerificationStatus.VERIFIED,
            ReviewNotes = "RC verified against transport portal"
        };

        var response = await service.ReviewDocumentAsync(adminPrincipal, vehicle.Uuid, doc.Id, request);

        Assert.Equal(DocumentVerificationStatus.VERIFIED, response.VerificationStatus);
        Assert.Equal("RC verified against transport portal", response.ReviewNotes);

        // Verify history entry
        var history = db.VehicleVerificationHistories.FirstOrDefault(h => h.DocumentId == doc.Id);
        Assert.NotNull(history);
        Assert.Equal("DOCUMENT_REVIEW", history.ActionType);
        Assert.Equal("VERIFIED", history.NewStatus);
    }

    [Fact]
    public async Task ReviewVehicleAsync_ApprovedWithoutMandatoryDocuments_ThrowsInvalidStatusTransitionException()
    {
        var (db, service, vehicle, driver, adminPrincipal) = CreateContextAndService();

        var request = new ReviewVehicleRequest
        {
            Status = VehicleVerificationStatus.VERIFIED
        };

        await Assert.ThrowsAsync<InvalidStatusTransitionException>(() =>
            service.ReviewVehicleAsync(adminPrincipal, vehicle.Uuid, request));
    }

    [Fact]
    public async Task ReviewVehicleAsync_ApprovedWithAllMandatoryDocuments_Succeeds()
    {
        var (db, service, vehicle, driver, adminPrincipal) = CreateContextAndService();

        // Seed 3 verified mandatory documents
        db.VehicleDocuments.AddRange(
            new VehicleDocument
            {
                VehicleId = vehicle.Id,
                DocumentType = VehicleDocumentType.REGISTRATION_CERTIFICATE,
                FileName = "rc.pdf",
                FilePath = "uploads/rc.pdf",
                ContentType = "application/pdf",
                FileSize = 1000,
                VerificationStatus = DocumentVerificationStatus.VERIFIED,
                IsCurrent = true
            },
            new VehicleDocument
            {
                VehicleId = vehicle.Id,
                DocumentType = VehicleDocumentType.VEHICLE_INSURANCE,
                FileName = "ins.pdf",
                FilePath = "uploads/ins.pdf",
                ContentType = "application/pdf",
                FileSize = 1000,
                VerificationStatus = DocumentVerificationStatus.VERIFIED,
                IsCurrent = true
            },
            new VehicleDocument
            {
                VehicleId = vehicle.Id,
                DocumentType = VehicleDocumentType.POLLUTION_UNDER_CONTROL,
                FileName = "puc.pdf",
                FilePath = "uploads/puc.pdf",
                ContentType = "application/pdf",
                FileSize = 1000,
                VerificationStatus = DocumentVerificationStatus.VERIFIED,
                IsCurrent = true
            }
        );
        db.SaveChanges();

        var request = new ReviewVehicleRequest
        {
            Status = VehicleVerificationStatus.VERIFIED,
            Notes = "All vehicle compliance requirements met"
        };

        var response = await service.ReviewVehicleAsync(adminPrincipal, vehicle.Uuid, request);

        Assert.Equal(VehicleVerificationStatus.VERIFIED, response.VerificationStatus);
        Assert.True(response.IsInspected);
        Assert.NotNull(response.VerifiedAt);

        // Verify audit history
        var history = db.VehicleVerificationHistories.FirstOrDefault(h => h.ActionType == "VEHICLE_VERIFICATION");
        Assert.NotNull(history);
        Assert.Equal("VERIFIED", history.NewStatus);
    }

    [Fact]
    public async Task ReviewVehicleAsync_RejectedWithoutReason_ThrowsBadRequestException()
    {
        var (db, service, vehicle, driver, adminPrincipal) = CreateContextAndService();

        var request = new ReviewVehicleRequest
        {
            Status = VehicleVerificationStatus.REJECTED,
            RejectionReason = null // missing reason
        };

        await Assert.ThrowsAsync<BadRequestException>(() =>
            service.ReviewVehicleAsync(adminPrincipal, vehicle.Uuid, request));
    }

    [Fact]
    public async Task SuspendVehicle_DeactivatesVehicleAndAssignments()
    {
        var (db, service, vehicle, driver, adminPrincipal) = CreateContextAndService();

        vehicle.VerificationStatus = VehicleVerificationStatus.VERIFIED;
        vehicle.IsActive = true;

        var assignment = new DriverVehicleAssignment
        {
            VehicleId = vehicle.Id,
            DriverId = driver.Id,
            Status = AssignmentStatus.ACTIVE,
            AssignedAt = DateTime.UtcNow
        };
        db.DriverVehicleAssignments.Add(assignment);
        db.SaveChanges();

        var suspendReq = new SuspendVehicleRequest
        {
            Action = "SUSPEND",
            Reason = "Inspection expired"
        };

        var response = await service.SuspendOrReinstateVehicleAsync(adminPrincipal, vehicle.Uuid, suspendReq);

        Assert.Equal(VehicleVerificationStatus.SUSPENDED, response.VerificationStatus);
        Assert.False(response.IsActive);

        var updatedAssignment = db.DriverVehicleAssignments.First(a => a.Id == assignment.Id);
        Assert.Equal(AssignmentStatus.INACTIVE, updatedAssignment.Status);
        Assert.NotNull(updatedAssignment.UnassignedAt);
    }

    [Fact]
    public async Task ReinstateVehicle_SuspendedVehicle_ReinstatesSuccessfully()
    {
        var (db, service, vehicle, driver, adminPrincipal) = CreateContextAndService();

        vehicle.VerificationStatus = VehicleVerificationStatus.SUSPENDED;
        vehicle.IsActive = false;
        db.SaveChanges();

        var reinstateReq = new SuspendVehicleRequest
        {
            Action = "REINSTATE",
            Reason = "Re-inspected and approved"
        };

        var response = await service.SuspendOrReinstateVehicleAsync(adminPrincipal, vehicle.Uuid, reinstateReq);

        Assert.Equal(VehicleVerificationStatus.VERIFIED, response.VerificationStatus);
        Assert.True(response.IsActive);
    }

    [Fact]
    public async Task AssignDriverAsync_EligibleDriverAndVerifiedVehicle_Succeeds()
    {
        var (db, service, vehicle, driver, adminPrincipal) = CreateContextAndService();

        vehicle.VerificationStatus = VehicleVerificationStatus.VERIFIED;
        vehicle.IsActive = true;
        db.SaveChanges();

        var assignReq = new AssignDriverRequest
        {
            DriverUuid = driver.User.Uuid,
            Reason = "Fleet deployment"
        };

        var response = await service.AssignDriverAsync(adminPrincipal, vehicle.Uuid, assignReq);

        Assert.NotNull(response);
        Assert.Equal(AssignmentStatus.ACTIVE, response.Status);
        Assert.Equal(vehicle.Uuid, response.VehicleUuid);
        Assert.Equal(driver.User.Uuid, response.DriverUuid);
    }

    [Fact]
    public async Task AssignDriverAsync_UnverifiedVehicle_ThrowsInvalidStatusTransitionException()
    {
        var (db, service, vehicle, driver, adminPrincipal) = CreateContextAndService();

        vehicle.VerificationStatus = VehicleVerificationStatus.PENDING_VERIFICATION; // not verified!
        db.SaveChanges();

        var assignReq = new AssignDriverRequest
        {
            DriverUuid = driver.User.Uuid
        };

        await Assert.ThrowsAsync<InvalidStatusTransitionException>(() =>
            service.AssignDriverAsync(adminPrincipal, vehicle.Uuid, assignReq));
    }
}
