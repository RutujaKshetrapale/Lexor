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
using Xunit;
using VehicleServiceImpl = VehicleService.Services.VehicleService;

namespace VehicleService.Tests;

public class VehicleServiceTests
{
    private static (LexorDbContext db, VehicleServiceImpl service, ClaimsPrincipal driverPrincipal) CreateContextAndService()
    {
        var db = TestDbContextFactory.Create("VehicleServiceTests_" + Guid.NewGuid().ToString("N"));

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
            LicenseNumber = "DL-KA01-2020-001234",
            IsVerified = true,
            OnboardingStatus = OnboardingStatus.VERIFIED,
            Status = DriverStatus.ONLINE
        };
        db.DriverProfiles.Add(driver);

        // Another driver for cross-access tests
        var otherUser = new User
        {
            Id = 2,
            Uuid = "driver-uuid-2",
            FullName = "Ramesh Kumar",
            Email = "driver2@lexor.com",
            PasswordHash = "hash",
            Role = Role.DRIVER,
            Status = UserStatus.ACTIVE
        };
        db.Users.Add(otherUser);

        var otherDriver = new DriverProfile
        {
            Id = 2,
            UserId = 2,
            User = otherUser,
            LicenseNumber = "DL-KA01-2020-005678",
            IsVerified = true,
            OnboardingStatus = OnboardingStatus.VERIFIED,
            Status = DriverStatus.ONLINE
        };
        db.DriverProfiles.Add(otherDriver);

        db.SaveChanges();

        var policyOptions = Options.Create(new VehicleVerificationPolicySettings
        {
            RequiredDocuments = "REGISTRATION_CERTIFICATE,VEHICLE_INSURANCE,POLLUTION_UNDER_CONTROL"
        });
        var policyService = new VehicleVerificationPolicyService(policyOptions);
        var logger = new Mock<ILogger<VehicleServiceImpl>>();

        var service = new VehicleServiceImpl(db, policyService, logger.Object);

        var driverPrincipal = new ClaimsPrincipal(new ClaimsIdentity(new[]
        {
            new Claim("sub", "driver-uuid-1"),
            new Claim("userId", "1"),
            new Claim(ClaimTypes.Role, "ROLE_DRIVER"),
            new Claim("role", "ROLE_DRIVER")
        }, "TestAuth"));

        return (db, service, driverPrincipal);
    }

    [Fact]
    public async Task RegisterVehicleAsync_ValidRequest_RegistersVehicleAndCreatesInitialAssignment()
    {
        var (db, service, driverPrincipal) = CreateContextAndService();

        var request = new RegisterVehicleRequest
        {
            Make = "Toyota",
            Model = "Hyryder",
            Year = 2023,
            Color = "Black",
            LicensePlate = "KA-01-EQ-4829",
            Vin = "MA31234567890ABCD",
            RideTypeCategory = "SEDAN",
            SeatCapacity = 4,
            FuelType = FuelType.HYBRID
        };

        var response = await service.RegisterVehicleAsync(driverPrincipal, request);

        Assert.NotNull(response);
        Assert.Equal("KA-01-EQ-4829", response.LicensePlate);
        Assert.Equal(VehicleVerificationStatus.PENDING_VERIFICATION, response.VerificationStatus);
        Assert.True(response.IsActive);
        Assert.False(response.IsInspected);

        // Verify vehicle stored
        var vehicle = db.Vehicles.FirstOrDefault(v => v.Uuid == response.Uuid);
        Assert.NotNull(vehicle);
        Assert.Equal(1, vehicle.DriverId);

        // Verify initial assignment created
        var assignment = db.DriverVehicleAssignments.FirstOrDefault(a => a.VehicleId == vehicle.Id);
        Assert.NotNull(assignment);
        Assert.Equal(AssignmentStatus.ACTIVE, assignment.Status);
        Assert.Equal(1, assignment.DriverId);
    }

    [Fact]
    public async Task RegisterVehicleAsync_NormalizesLicensePlate()
    {
        var (db, service, driverPrincipal) = CreateContextAndService();

        var request = new RegisterVehicleRequest
        {
            Make = "Hyundai",
            Model = "Creta",
            Year = 2022,
            Color = "White",
            LicensePlate = "  ka   05  ab  1234  ",
            RideTypeCategory = "SUV",
            SeatCapacity = 5,
            FuelType = FuelType.PETROL
        };

        var response = await service.RegisterVehicleAsync(driverPrincipal, request);

        Assert.Equal("KA-05-AB-1234", response.LicensePlate);
    }

    [Fact]
    public async Task RegisterVehicleAsync_DuplicateLicensePlate_ThrowsDuplicateResourceException()
    {
        var (db, service, driverPrincipal) = CreateContextAndService();

        var request = new RegisterVehicleRequest
        {
            Make = "Toyota",
            Model = "Hyryder",
            Year = 2023,
            Color = "Black",
            LicensePlate = "KA-01-EQ-4829",
            RideTypeCategory = "SEDAN",
            SeatCapacity = 4,
            FuelType = FuelType.HYBRID
        };

        await service.RegisterVehicleAsync(driverPrincipal, request);

        // Attempt registering same plate
        await Assert.ThrowsAsync<DuplicateResourceException>(() =>
            service.RegisterVehicleAsync(driverPrincipal, request));
    }

    [Fact]
    public async Task RegisterVehicleAsync_DuplicateVin_ThrowsDuplicateResourceException()
    {
        var (db, service, driverPrincipal) = CreateContextAndService();

        var request1 = new RegisterVehicleRequest
        {
            Make = "Toyota",
            Model = "Hyryder",
            Year = 2023,
            Color = "Black",
            LicensePlate = "KA-01-EQ-4829",
            Vin = "VIN1234567890ABCD",
            RideTypeCategory = "SEDAN",
            SeatCapacity = 4,
            FuelType = FuelType.HYBRID
        };

        var request2 = new RegisterVehicleRequest
        {
            Make = "Honda",
            Model = "City",
            Year = 2023,
            Color = "Silver",
            LicensePlate = "KA-02-ZZ-9999",
            Vin = "VIN1234567890ABCD", // duplicate VIN
            RideTypeCategory = "SEDAN",
            SeatCapacity = 4,
            FuelType = FuelType.PETROL
        };

        await service.RegisterVehicleAsync(driverPrincipal, request1);
        await Assert.ThrowsAsync<DuplicateResourceException>(() =>
            service.RegisterVehicleAsync(driverPrincipal, request2));
    }

    [Fact]
    public async Task GetVehicleByUuidAsync_CrossDriverAccess_ThrowsForbiddenException()
    {
        var (db, service, driverPrincipal) = CreateContextAndService();

        // Register vehicle for Driver 1
        var reg = await service.RegisterVehicleAsync(driverPrincipal, new RegisterVehicleRequest
        {
            Make = "Tata",
            Model = "Nexon",
            Year = 2023,
            Color = "Blue",
            LicensePlate = "KA-03-MM-1111",
            RideTypeCategory = "SUV",
            SeatCapacity = 5,
            FuelType = FuelType.ELECTRIC
        });

        // Driver 2 attempts to get Driver 1's vehicle
        var driver2Principal = new ClaimsPrincipal(new ClaimsIdentity(new[]
        {
            new Claim("sub", "driver-uuid-2"),
            new Claim("userId", "2"),
            new Claim("role", "ROLE_DRIVER")
        }, "TestAuth"));

        await Assert.ThrowsAsync<ForbiddenException>(() =>
            service.GetVehicleByUuidAsync(driver2Principal, reg.Uuid));
    }

    [Fact]
    public async Task UpdateVehicleAsync_ValidRequest_UpdatesFields()
    {
        var (db, service, driverPrincipal) = CreateContextAndService();

        var reg = await service.RegisterVehicleAsync(driverPrincipal, new RegisterVehicleRequest
        {
            Make = "Toyota",
            Model = "Hyryder",
            Year = 2023,
            Color = "Black",
            LicensePlate = "KA-01-EQ-4829",
            RideTypeCategory = "SEDAN",
            SeatCapacity = 4,
            FuelType = FuelType.HYBRID
        });

        var updated = await service.UpdateVehicleAsync(driverPrincipal, reg.Uuid, new UpdateVehicleRequest
        {
            Color = "Pearl White",
            SeatCapacity = 5
        });

        Assert.Equal("Pearl White", updated.Color);
        Assert.Equal(5, updated.SeatCapacity);
    }

    [Fact]
    public async Task UpdateVehicleStatusAsync_ActivateUnverified_ThrowsInvalidStatusTransitionException()
    {
        var (db, service, driverPrincipal) = CreateContextAndService();

        var reg = await service.RegisterVehicleAsync(driverPrincipal, new RegisterVehicleRequest
        {
            Make = "Toyota",
            Model = "Hyryder",
            Year = 2023,
            Color = "Black",
            LicensePlate = "KA-01-EQ-4829",
            RideTypeCategory = "SEDAN",
            SeatCapacity = 4,
            FuelType = FuelType.HYBRID
        });

        // Vehicle is PENDING_VERIFICATION; activating must fail
        await Assert.ThrowsAsync<InvalidStatusTransitionException>(() =>
            service.UpdateVehicleStatusAsync(driverPrincipal, reg.Uuid, true));
    }

    [Fact]
    public async Task SubmitForVerificationAsync_MissingMandatoryDocuments_ThrowsBadRequestException()
    {
        var (db, service, driverPrincipal) = CreateContextAndService();

        var reg = await service.RegisterVehicleAsync(driverPrincipal, new RegisterVehicleRequest
        {
            Make = "Toyota",
            Model = "Hyryder",
            Year = 2023,
            Color = "Black",
            LicensePlate = "KA-01-EQ-4829",
            RideTypeCategory = "SEDAN",
            SeatCapacity = 4,
            FuelType = FuelType.HYBRID
        });

        // No documents uploaded yet
        await Assert.ThrowsAsync<BadRequestException>(() =>
            service.SubmitForVerificationAsync(driverPrincipal, reg.Uuid));
    }

    [Fact]
    public async Task SubmitForVerificationAsync_AllMandatoryDocumentsPresent_Succeeds()
    {
        var (db, service, driverPrincipal) = CreateContextAndService();

        var reg = await service.RegisterVehicleAsync(driverPrincipal, new RegisterVehicleRequest
        {
            Make = "Toyota",
            Model = "Hyryder",
            Year = 2023,
            Color = "Black",
            LicensePlate = "KA-01-EQ-4829",
            RideTypeCategory = "SEDAN",
            SeatCapacity = 4,
            FuelType = FuelType.HYBRID
        });

        var vehicle = db.Vehicles.First(v => v.Uuid == reg.Uuid);

        // Add mandatory documents
        db.VehicleDocuments.AddRange(
            new VehicleDocument
            {
                VehicleId = vehicle.Id,
                DocumentType = VehicleDocumentType.REGISTRATION_CERTIFICATE,
                FileName = "rc.pdf",
                FilePath = "uploads/rc.pdf",
                ContentType = "application/pdf",
                FileSize = 1000,
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
                IsCurrent = true
            }
        );
        db.SaveChanges();

        var response = await service.SubmitForVerificationAsync(driverPrincipal, reg.Uuid);
        Assert.Equal(VehicleVerificationStatus.PENDING_VERIFICATION, response.VerificationStatus);
    }

    [Fact]
    public async Task GetVerificationStatusAsync_CalculatesChecklistCorrectly()
    {
        var (db, service, driverPrincipal) = CreateContextAndService();

        var reg = await service.RegisterVehicleAsync(driverPrincipal, new RegisterVehicleRequest
        {
            Make = "Toyota",
            Model = "Hyryder",
            Year = 2023,
            Color = "Black",
            LicensePlate = "KA-01-EQ-4829",
            RideTypeCategory = "SEDAN",
            SeatCapacity = 4,
            FuelType = FuelType.HYBRID
        });

        var status = await service.GetVerificationStatusAsync(driverPrincipal, reg.Uuid);

        Assert.Equal(3, status.RequiredDocuments.Count);
        Assert.Equal(3, status.MissingDocuments.Count);
        Assert.False(status.IsEligibleForAssignment);
    }
}
