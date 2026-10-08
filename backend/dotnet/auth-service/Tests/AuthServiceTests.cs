using AuthService.Data;
using AuthService.DTOs;
using AuthService.Entities;
using AuthService.Exceptions;
using AuthService.Security;
using AuthService.Services;
using Microsoft.EntityFrameworkCore;
using Moq;
using Xunit;

namespace AuthService.Tests;

public class AuthServiceTests
{
    private LexorDbContext GetInMemoryDbContext()
    {
        var options = new DbContextOptionsBuilder<LexorDbContext>()
            .UseInMemoryDatabase(databaseName: Guid.NewGuid().ToString())
            .Options;

        return new LexorDbContext(options);
    }

    [Fact]
    public async Task RegisterAsync_SuccessfulRiderRegistration_ReturnsAuthResponse()
    {
        // Arrange
        using var dbContext = GetInMemoryDbContext();
        var mockHasher = new Mock<IPasswordHasher>();
        mockHasher.Setup(h => h.HashPassword(It.IsAny<string>())).Returns("$2a$12$SampleBCryptHash");

        var mockJwt = new Mock<IJwtTokenService>();
        mockJwt.Setup(j => j.GenerateToken(It.IsAny<User>())).Returns("mock.jwt.token");
        mockJwt.Setup(j => j.GetExpirationSeconds()).Returns(86400);

        var authService = new AuthService.Services.AuthService(dbContext, mockHasher.Object, mockJwt.Object);

        var request = new RegisterRequest
        {
            FirstName = "Rajesh",
            LastName = "Kumar",
            Email = "rajesh.kumar@example.com",
            PhoneNumber = "+919876543211",
            Password = "Password123",
            Role = UserRole.RIDER
        };

        // Act
        var response = await authService.RegisterAsync(request);

        // Assert
        Assert.NotNull(response);
        Assert.Equal("mock.jwt.token", response.AccessToken);
        Assert.Equal("Bearer", response.TokenType);
        Assert.Equal("rajesh.kumar@example.com", response.User.Email);
        Assert.Equal(UserRole.RIDER, response.User.Role);
        Assert.Equal(1, await dbContext.Users.CountAsync());
    }

    [Fact]
    public async Task RegisterAsync_SuccessfulDriverRegistration_ReturnsAuthResponse()
    {
        // Arrange
        using var dbContext = GetInMemoryDbContext();
        var mockHasher = new Mock<IPasswordHasher>();
        mockHasher.Setup(h => h.HashPassword(It.IsAny<string>())).Returns("$2a$12$SampleBCryptHash");

        var mockJwt = new Mock<IJwtTokenService>();
        mockJwt.Setup(j => j.GenerateToken(It.IsAny<User>())).Returns("mock.jwt.token");
        mockJwt.Setup(j => j.GetExpirationSeconds()).Returns(86400);

        var authService = new AuthService.Services.AuthService(dbContext, mockHasher.Object, mockJwt.Object);

        var request = new RegisterRequest
        {
            FirstName = "Vikram",
            LastName = "Singh",
            Email = "vikram.singh@example.com",
            PhoneNumber = "+919876543212",
            Password = "Password123",
            Role = UserRole.DRIVER
        };

        // Act
        var response = await authService.RegisterAsync(request);

        // Assert
        Assert.NotNull(response);
        Assert.Equal(UserRole.DRIVER, response.User.Role);
    }

    [Fact]
    public async Task RegisterAsync_AdminRegistrationAttempt_ThrowsForbiddenException()
    {
        // Arrange
        using var dbContext = GetInMemoryDbContext();
        var mockHasher = new Mock<IPasswordHasher>();
        var mockJwt = new Mock<IJwtTokenService>();
        var authService = new AuthService.Services.AuthService(dbContext, mockHasher.Object, mockJwt.Object);

        var request = new RegisterRequest
        {
            FirstName = "Admin",
            LastName = "User",
            Email = "admin@example.com",
            PhoneNumber = "+919876543299",
            Password = "Password123",
            Role = UserRole.ADMIN
        };

        // Act & Assert
        await Assert.ThrowsAsync<ForbiddenException>(() => authService.RegisterAsync(request));
    }

    [Fact]
    public async Task RegisterAsync_DuplicateEmail_ThrowsDuplicateResourceException()
    {
        // Arrange
        using var dbContext = GetInMemoryDbContext();
        dbContext.Users.Add(new User
        {
            Uuid = Guid.NewGuid().ToString(),
            FirstName = "Existing",
            LastName = "User",
            Email = "rajesh.kumar@example.com",
            PhoneNumber = "+919876543200",
            PasswordHash = "hash",
            Role = UserRole.RIDER,
            Status = UserStatus.ACTIVE
        });
        await dbContext.SaveChangesAsync();

        var mockHasher = new Mock<IPasswordHasher>();
        var mockJwt = new Mock<IJwtTokenService>();
        var authService = new AuthService.Services.AuthService(dbContext, mockHasher.Object, mockJwt.Object);

        var request = new RegisterRequest
        {
            FirstName = "Rajesh",
            LastName = "Kumar",
            Email = "rajesh.kumar@example.com",
            PhoneNumber = "+919876543211",
            Password = "Password123",
            Role = UserRole.RIDER
        };

        // Act & Assert
        await Assert.ThrowsAsync<DuplicateResourceException>(() => authService.RegisterAsync(request));
    }

    [Fact]
    public async Task LoginAsync_ValidCredentials_ReturnsAuthResponse()
    {
        // Arrange
        using var dbContext = GetInMemoryDbContext();
        var user = new User
        {
            Id = 1,
            Uuid = Guid.NewGuid().ToString(),
            FirstName = "Rajesh",
            LastName = "Kumar",
            Email = "rajesh.kumar@example.com",
            PhoneNumber = "+919876543211",
            PasswordHash = "$2a$12$SampleBCryptHash",
            Role = UserRole.RIDER,
            Status = UserStatus.ACTIVE
        };
        dbContext.Users.Add(user);
        await dbContext.SaveChangesAsync();

        var mockHasher = new Mock<IPasswordHasher>();
        mockHasher.Setup(h => h.VerifyPassword("Password123", "$2a$12$SampleBCryptHash")).Returns(true);

        var mockJwt = new Mock<IJwtTokenService>();
        mockJwt.Setup(j => j.GenerateToken(It.IsAny<User>())).Returns("mock.jwt.token");
        mockJwt.Setup(j => j.GetExpirationSeconds()).Returns(86400);

        var authService = new AuthService.Services.AuthService(dbContext, mockHasher.Object, mockJwt.Object);

        var request = new LoginRequest
        {
            Email = "rajesh.kumar@example.com",
            Password = "Password123"
        };

        // Act
        var response = await authService.LoginAsync(request);

        // Assert
        Assert.NotNull(response);
        Assert.Equal("mock.jwt.token", response.AccessToken);
        Assert.Equal("rajesh.kumar@example.com", response.User.Email);
    }

    [Fact]
    public async Task LoginAsync_InvalidPassword_ThrowsInvalidCredentialsException()
    {
        // Arrange
        using var dbContext = GetInMemoryDbContext();
        var user = new User
        {
            Email = "rajesh.kumar@example.com",
            PasswordHash = "$2a$12$SampleBCryptHash",
            Status = UserStatus.ACTIVE
        };
        dbContext.Users.Add(user);
        await dbContext.SaveChangesAsync();

        var mockHasher = new Mock<IPasswordHasher>();
        mockHasher.Setup(h => h.VerifyPassword("WrongPassword", "$2a$12$SampleBCryptHash")).Returns(false);

        var mockJwt = new Mock<IJwtTokenService>();
        var authService = new AuthService.Services.AuthService(dbContext, mockHasher.Object, mockJwt.Object);

        var request = new LoginRequest
        {
            Email = "rajesh.kumar@example.com",
            Password = "WrongPassword"
        };

        // Act & Assert
        await Assert.ThrowsAsync<InvalidCredentialsException>(() => authService.LoginAsync(request));
    }
}
