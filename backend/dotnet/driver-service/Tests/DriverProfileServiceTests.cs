using DriverService.Configuration;
using DriverService.DTOs;
using DriverService.DTOs.Request;
using DriverService.Entities;
using DriverService.Enums;
using DriverService.Exceptions;
using DriverService.Services;
using Microsoft.Extensions.Options;
using Xunit;

namespace DriverService.Tests;

public class DriverProfileServiceTests
{
    private static IOptions<OnboardingPolicySettings> CreatePolicySettings()
    {
        return Options.Create(new OnboardingPolicySettings
        {
            RequiredDocuments = "DRIVING_LICENSE,GOVERNMENT_ID,ADDRESS_PROOF"
        });
    }

    [Fact]
    public async Task GetDriverProfile_NonExistingProfile_InitializesNewProfile()
    {
        using var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var policyService = new OnboardingPolicyService(db, CreatePolicySettings());
        var profileService = new DriverProfileService(db, policyService);

        var user = new User { Id = 1, Uuid = "u-uuid-1", FirstName = "Vikram", LastName = "Singh", Email = "vikram@test.com", PhoneNumber = "+919876543210" };
        db.Users.Add(user);
        await db.SaveChangesAsync();

        var profile = await profileService.GetDriverProfileAsync("u-uuid-1");

        Assert.NotNull(profile);
        Assert.Equal("Vikram", profile.FirstName);
        Assert.Equal(DriverStatus.OFFLINE, profile.Status);
        Assert.Equal(OnboardingStatus.INCOMPLETE, profile.OnboardingStatus);
    }

    [Fact]
    public async Task UpdateDriverProfile_ValidData_UpdatesSuccessfully()
    {
        using var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var policyService = new OnboardingPolicyService(db, CreatePolicySettings());
        var profileService = new DriverProfileService(db, policyService);

        var user = new User { Id = 1, Uuid = "u-uuid-1", FirstName = "Vikram", LastName = "Singh", Email = "vikram@test.com", PhoneNumber = "+919876543210" };
        db.Users.Add(user);
        await db.SaveChangesAsync();

        var request = new UpdateDriverProfileRequest
        {
            LicenseNumber = "DL-MH-2024-001234",
            LicenseExpiryDate = DateOnly.FromDateTime(DateTime.UtcNow.AddYears(5)),
            LicenseIssueDate = DateOnly.FromDateTime(DateTime.UtcNow.AddYears(-2)),
            LicenseIssuingAuthority = "RTO Mumbai West",
            LicenseIssuingState = "Maharashtra",
            DateOfBirth = DateOnly.FromDateTime(DateTime.UtcNow.AddYears(-28)),
            ResidentialAddress = "Flat 402, Sea Green Apts, Bandra",
            City = "Mumbai",
            State = "Maharashtra",
            PostalCode = "400050",
            Country = "India",
            EmergencyContact = new EmergencyContactDto
            {
                ContactName = "Sunita Singh",
                ContactPhone = "+919820098200",
                Relationship = "Spouse"
            }
        };

        var response = await profileService.UpdateDriverProfileAsync("u-uuid-1", request);

        Assert.NotNull(response);
        Assert.Equal("DL-MH-2024-001234", response.LicenseNumber);
        Assert.Equal("Mumbai", response.City);
        Assert.NotNull(response.EmergencyContact);
        Assert.Equal("Sunita Singh", response.EmergencyContact.ContactName);
    }

    [Fact]
    public async Task UpdateDriverProfile_Under18_ThrowsBadRequest()
    {
        using var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var policyService = new OnboardingPolicyService(db, CreatePolicySettings());
        var profileService = new DriverProfileService(db, policyService);

        var user = new User { Id = 1, Uuid = "u-uuid-1", FirstName = "Vikram", LastName = "Singh", Email = "vikram@test.com", PhoneNumber = "+919876543210" };
        db.Users.Add(user);
        await db.SaveChangesAsync();

        var request = new UpdateDriverProfileRequest
        {
            LicenseNumber = "DL-TEST-01",
            LicenseExpiryDate = DateOnly.FromDateTime(DateTime.UtcNow.AddYears(2)),
            DateOfBirth = DateOnly.FromDateTime(DateTime.UtcNow.AddYears(-16)), // 16 years old
            ResidentialAddress = "123 Main St",
            City = "Mumbai",
            State = "Maharashtra",
            PostalCode = "400001"
        };

        await Assert.ThrowsAsync<BadRequestException>(() => profileService.UpdateDriverProfileAsync("u-uuid-1", request));
    }

    [Fact]
    public async Task UpdateDriverProfile_ExpiryBeforeIssueDate_ThrowsBadRequest()
    {
        using var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var policyService = new OnboardingPolicyService(db, CreatePolicySettings());
        var profileService = new DriverProfileService(db, policyService);

        var user = new User { Id = 1, Uuid = "u-uuid-1", FirstName = "Vikram", LastName = "Singh", Email = "vikram@test.com", PhoneNumber = "+919876543210" };
        db.Users.Add(user);
        await db.SaveChangesAsync();

        var request = new UpdateDriverProfileRequest
        {
            LicenseNumber = "DL-TEST-01",
            LicenseIssueDate = DateOnly.FromDateTime(DateTime.UtcNow.AddYears(1)),
            LicenseExpiryDate = DateOnly.FromDateTime(DateTime.UtcNow.AddYears(-1)), // Expiry before issue!
            DateOfBirth = DateOnly.FromDateTime(DateTime.UtcNow.AddYears(-25)),
            ResidentialAddress = "123 Main St",
            City = "Mumbai",
            State = "Maharashtra",
            PostalCode = "400001"
        };

        await Assert.ThrowsAsync<BadRequestException>(() => profileService.UpdateDriverProfileAsync("u-uuid-1", request));
    }

    [Fact]
    public async Task UpdateAvailability_WhenNotVerified_ThrowsForbidden()
    {
        using var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var policyService = new OnboardingPolicyService(db, CreatePolicySettings());
        var profileService = new DriverProfileService(db, policyService);

        var user = new User { Id = 1, Uuid = "u-uuid-1", FirstName = "Vikram", LastName = "Singh", Status = UserStatus.ACTIVE };
        db.Users.Add(user);
        var driver = new DriverProfile
        {
            Id = 1,
            UserId = 1,
            User = user,
            OnboardingStatus = OnboardingStatus.INCOMPLETE,
            IsVerified = false
        };
        db.DriverProfiles.Add(driver);
        await db.SaveChangesAsync();

        var request = new DriverAvailabilityRequest { IsOnline = true };

        await Assert.ThrowsAsync<ForbiddenException>(() => profileService.UpdateAvailabilityAsync("u-uuid-1", request));
    }

    [Fact]
    public async Task UpdateAvailability_WhenVerifiedAndActive_Succeeds()
    {
        using var db = TestDbContextFactory.Create(Guid.NewGuid().ToString());
        var policyService = new OnboardingPolicyService(db, CreatePolicySettings());
        var profileService = new DriverProfileService(db, policyService);

        var user = new User { Id = 1, Uuid = "u-uuid-1", FirstName = "Vikram", LastName = "Singh", Status = UserStatus.ACTIVE };
        db.Users.Add(user);
        var driver = new DriverProfile
        {
            Id = 1,
            UserId = 1,
            User = user,
            OnboardingStatus = OnboardingStatus.VERIFIED,
            IsVerified = true,
            Status = DriverStatus.OFFLINE
        };
        db.DriverProfiles.Add(driver);
        await db.SaveChangesAsync();

        var request = new DriverAvailabilityRequest { IsOnline = true };
        var response = await profileService.UpdateAvailabilityAsync("u-uuid-1", request);

        Assert.True(response.IsOnline);
        Assert.Equal(DriverStatus.ONLINE, response.Status);
    }
}
