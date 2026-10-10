using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging.Abstractions;
using Moq;
using UserService.Data;
using UserService.DTOs.Request;
using UserService.Entities;
using UserService.Exceptions;
using UserService.Security;
using Xunit;

namespace UserService.Tests;

public class UserServiceTests
{
    private LexorDbContext GetInMemoryDbContext()
    {
        var options = new DbContextOptionsBuilder<LexorDbContext>()
            .UseInMemoryDatabase(databaseName: Guid.NewGuid().ToString())
            .Options;
        return new LexorDbContext(options);
    }

    private Mock<IPasswordHasher> GetMockPasswordHasher()
    {
        var mock = new Mock<IPasswordHasher>();
        mock.Setup(h => h.HashPassword(It.IsAny<string>())).Returns("hashed_password_sample");
        return mock;
    }

    [Fact]
    public async Task GetCurrentUserProfileAsync_ShouldReturnProfile_WhenUserExists()
    {
        using var dbContext = GetInMemoryDbContext();
        var user = new User
        {
            Id = 1,
            Uuid = "rider-uuid-101",
            FirstName = "Rajesh",
            LastName = "Kumar",
            Email = "rajesh@example.com",
            PhoneNumber = "+919876543211",
            PasswordHash = "hash",
            Role = UserRole.RIDER,
            Status = UserStatus.ACTIVE
        };
        dbContext.Users.Add(user);
        await dbContext.SaveChangesAsync();

        var service = new Services.UserService(dbContext, GetMockPasswordHasher().Object, NullLogger<Services.UserService>.Instance);

        var result = await service.GetCurrentUserProfileAsync("rider-uuid-101");

        Assert.NotNull(result);
        Assert.Equal("rider-uuid-101", result.Uuid);
        Assert.Equal("Rajesh", result.FirstName);
        Assert.Equal("rajesh@example.com", result.Email);
    }

    [Fact]
    public async Task UpdateCurrentUserProfileAsync_ShouldRejectDuplicatePhoneNumber()
    {
        using var dbContext = GetInMemoryDbContext();
        dbContext.Users.AddRange(
            new User { Id = 1, Uuid = "rider-uuid-101", FirstName = "User1", LastName = "One", Email = "u1@test.com", PhoneNumber = "+919876543211", PasswordHash = "h1", Role = UserRole.RIDER, Status = UserStatus.ACTIVE },
            new User { Id = 2, Uuid = "rider-uuid-102", FirstName = "User2", LastName = "Two", Email = "u2@test.com", PhoneNumber = "+919876543222", PasswordHash = "h2", Role = UserRole.RIDER, Status = UserStatus.ACTIVE }
        );
        await dbContext.SaveChangesAsync();

        var service = new Services.UserService(dbContext, GetMockPasswordHasher().Object, NullLogger<Services.UserService>.Instance);

        var request = new UpdateProfileRequest
        {
            FirstName = "User1",
            LastName = "Updated",
            PhoneNumber = "+919876543222" // Already used by user 2
        };

        await Assert.ThrowsAsync<DuplicateResourceException>(() => service.UpdateCurrentUserProfileAsync("rider-uuid-101", request));
    }

    [Fact]
    public async Task DeactivateCurrentUserAsync_ShouldSoftDeactivate_WhenRoleIsRider()
    {
        using var dbContext = GetInMemoryDbContext();
        var user = new User { Id = 1, Uuid = "rider-uuid-101", FirstName = "User1", LastName = "One", Email = "u1@test.com", PhoneNumber = "+919876543211", PasswordHash = "h1", Role = UserRole.RIDER, Status = UserStatus.ACTIVE };
        dbContext.Users.Add(user);
        await dbContext.SaveChangesAsync();

        var service = new Services.UserService(dbContext, GetMockPasswordHasher().Object, NullLogger<Services.UserService>.Instance);

        await service.DeactivateCurrentUserAsync("rider-uuid-101");

        var updatedUser = await dbContext.Users.FirstAsync(u => u.Uuid == "rider-uuid-101");
        Assert.Equal(UserStatus.DEACTIVATED, updatedUser.Status);
    }

    [Fact]
    public async Task DeactivateCurrentUserAsync_ShouldForbidden_WhenRoleIsAdmin()
    {
        using var dbContext = GetInMemoryDbContext();
        var admin = new User { Id = 1, Uuid = "admin-uuid-001", FirstName = "Admin", LastName = "User", Email = "admin@test.com", PhoneNumber = "+919876543210", PasswordHash = "h1", Role = UserRole.ADMIN, Status = UserStatus.ACTIVE };
        dbContext.Users.Add(admin);
        await dbContext.SaveChangesAsync();

        var service = new Services.UserService(dbContext, GetMockPasswordHasher().Object, NullLogger<Services.UserService>.Instance);

        await Assert.ThrowsAsync<ForbiddenException>(() => service.DeactivateCurrentUserAsync("admin-uuid-001"));
    }

    [Fact]
    public async Task CreateUserAsync_ShouldProvisionUser_WhenValid()
    {
        using var dbContext = GetInMemoryDbContext();
        var service = new Services.UserService(dbContext, GetMockPasswordHasher().Object, NullLogger<Services.UserService>.Instance);

        var request = new AdminCreateUserRequest
        {
            FirstName = "Vikram",
            LastName = "Singh",
            Email = "vikram@example.com",
            PhoneNumber = "+919876543212",
            Password = "Password123!",
            Role = UserRole.DRIVER
        };

        var response = await service.CreateUserAsync(request);

        Assert.NotNull(response);
        Assert.Equal("Vikram", response.FirstName);
        Assert.Equal("vikram@example.com", response.Email);
        Assert.Equal(UserRole.DRIVER, response.Role);
        Assert.Equal(UserStatus.ACTIVE, response.Status);
    }

    [Fact]
    public async Task UpdateUserStatusAsync_ShouldThrowInvalidStatusTransition_WhenTransitionIsInvalid()
    {
        using var dbContext = GetInMemoryDbContext();
        var user = new User { Id = 1, Uuid = "rider-uuid-101", FirstName = "User1", LastName = "One", Email = "u1@test.com", PhoneNumber = "+919876543211", PasswordHash = "h1", Role = UserRole.RIDER, Status = UserStatus.DEACTIVATED };
        dbContext.Users.Add(user);
        await dbContext.SaveChangesAsync();

        var service = new Services.UserService(dbContext, GetMockPasswordHasher().Object, NullLogger<Services.UserService>.Instance);

        var request = new UpdateUserStatusRequest { Status = UserStatus.SUSPENDED };

        await Assert.ThrowsAsync<InvalidStatusTransitionException>(() => service.UpdateUserStatusAsync("admin-uuid-999", "rider-uuid-101", request));
    }

    [Fact]
    public async Task DeactivateUserByUuidAsync_ShouldProtectLastActiveAdmin()
    {
        using var dbContext = GetInMemoryDbContext();
        var admin = new User { Id = 1, Uuid = "admin-uuid-001", FirstName = "Admin", LastName = "User", Email = "admin@test.com", PhoneNumber = "+919876543210", PasswordHash = "h1", Role = UserRole.ADMIN, Status = UserStatus.ACTIVE };
        dbContext.Users.Add(admin);
        await dbContext.SaveChangesAsync();

        var service = new Services.UserService(dbContext, GetMockPasswordHasher().Object, NullLogger<Services.UserService>.Instance);

        await Assert.ThrowsAsync<ForbiddenException>(() => service.DeactivateUserByUuidAsync("other-admin-uuid", "admin-uuid-001"));
    }
}
