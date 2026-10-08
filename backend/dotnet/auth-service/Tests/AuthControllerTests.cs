using System.Security.Claims;
using AuthService.Controllers;
using AuthService.DTOs;
using AuthService.Entities;
using AuthService.Services;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Moq;
using Xunit;

namespace AuthService.Tests;

public class AuthControllerTests
{
    private readonly Mock<IAuthService> _authServiceMock;
    private readonly AuthController _controller;

    public AuthControllerTests()
    {
        _authServiceMock = new Mock<IAuthService>();
        _controller = new AuthController(_authServiceMock.Object);

        // Setup HttpContext
        var httpContext = new DefaultHttpContext();
        _controller.ControllerContext = new ControllerContext
        {
            HttpContext = httpContext
        };
    }

    [Fact]
    public async Task Register_Returns201Created_WithAuthResponse()
    {
        // Arrange
        var request = new RegisterRequest
        {
            FirstName = "Jane",
            LastName = "Doe",
            Email = "jane.doe@example.com",
            Password = "Password123!",
            PhoneNumber = "+1987654321",
            Role = UserRole.RIDER
        };

        var expectedUser = new UserResponse
        {
            Uuid = "user-uuid-123",
            FirstName = "Jane",
            LastName = "Doe",
            Email = "jane.doe@example.com",
            Role = UserRole.RIDER,
            Status = UserStatus.ACTIVE
        };

        var expectedAuthResponse = new AuthResponse
        {
            AccessToken = "mock.jwt.token",
            TokenType = "Bearer",
            ExpiresIn = 86400,
            User = expectedUser
        };

        _authServiceMock.Setup(s => s.RegisterAsync(It.IsAny<RegisterRequest>()))
            .ReturnsAsync(expectedAuthResponse);

        // Act
        var result = await _controller.Register(request);

        // Assert
        var createdResult = Assert.IsType<CreatedAtActionResult>(result.Result);
        Assert.Equal(StatusCodes.Status201Created, createdResult.StatusCode);
        var actualResponse = Assert.IsType<AuthResponse>(createdResult.Value);
        Assert.Equal("mock.jwt.token", actualResponse.AccessToken);
        Assert.Equal("jane.doe@example.com", actualResponse.User.Email);
    }

    [Fact]
    public async Task Login_Returns200Ok_WithAuthResponse()
    {
        // Arrange
        var request = new LoginRequest
        {
            Email = "jane.doe@example.com",
            Password = "Password123!"
        };

        var expectedAuthResponse = new AuthResponse
        {
            AccessToken = "mock.jwt.token",
            TokenType = "Bearer",
            ExpiresIn = 86400,
            User = new UserResponse
            {
                Uuid = "user-uuid-123",
                Email = "jane.doe@example.com",
                Role = UserRole.RIDER,
                Status = UserStatus.ACTIVE
            }
        };

        _authServiceMock.Setup(s => s.LoginAsync(It.IsAny<LoginRequest>()))
            .ReturnsAsync(expectedAuthResponse);

        // Act
        var result = await _controller.Login(request);

        // Assert
        var okResult = Assert.IsType<OkObjectResult>(result.Result);
        Assert.Equal(StatusCodes.Status200OK, okResult.StatusCode);
        var actualResponse = Assert.IsType<AuthResponse>(okResult.Value);
        Assert.Equal("mock.jwt.token", actualResponse.AccessToken);
    }

    [Fact]
    public async Task GetCurrentUser_Returns200Ok_WhenClaimExists()
    {
        // Arrange
        var userUuid = "user-uuid-123";
        var userClaims = new ClaimsPrincipal(new ClaimsIdentity(new[]
        {
            new Claim(ClaimTypes.NameIdentifier, userUuid)
        }, "TestAuth"));

        _controller.ControllerContext.HttpContext.User = userClaims;

        var expectedUser = new UserResponse
        {
            Uuid = userUuid,
            FirstName = "Jane",
            LastName = "Doe",
            Email = "jane.doe@example.com",
            Role = UserRole.RIDER,
            Status = UserStatus.ACTIVE
        };

        _authServiceMock.Setup(s => s.GetCurrentUserAsync(userUuid))
            .ReturnsAsync(expectedUser);

        // Act
        var result = await _controller.GetCurrentUser();

        // Assert
        var okResult = Assert.IsType<OkObjectResult>(result.Result);
        Assert.Equal(StatusCodes.Status200OK, okResult.StatusCode);
        var actualUser = Assert.IsType<UserResponse>(okResult.Value);
        Assert.Equal(userUuid, actualUser.Uuid);
    }

    [Fact]
    public async Task GetCurrentUser_Returns401Unauthorized_WhenUserClaimMissing()
    {
        // Arrange
        _controller.ControllerContext.HttpContext.User = new ClaimsPrincipal(new ClaimsIdentity());

        // Act
        var result = await _controller.GetCurrentUser();

        // Assert
        var unauthorizedResult = Assert.IsType<UnauthorizedObjectResult>(result.Result);
        Assert.Equal(StatusCodes.Status401Unauthorized, unauthorizedResult.StatusCode);
        var errorResponse = Assert.IsType<ErrorResponse>(unauthorizedResult.Value);
        Assert.Equal("UNAUTHORIZED", errorResponse.Error);
    }
}
