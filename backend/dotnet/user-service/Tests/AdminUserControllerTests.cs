using System.Security.Claims;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Moq;
using UserService.Controllers;
using UserService.DTOs.Request;
using UserService.DTOs.Response;
using UserService.Entities;
using UserService.Services;
using Xunit;

namespace UserService.Tests;

public class AdminUserControllerTests
{
    private readonly Mock<IUserService> _userServiceMock;
    private readonly AdminUserController _controller;

    public AdminUserControllerTests()
    {
        _userServiceMock = new Mock<IUserService>();
        _controller = new AdminUserController(_userServiceMock.Object);

        _controller.ControllerContext = new ControllerContext
        {
            HttpContext = new DefaultHttpContext()
        };
    }

    private void SetAdminContext(string uuid = "admin-uuid-001")
    {
        var claims = new List<Claim>
        {
            new(ClaimTypes.NameIdentifier, uuid),
            new(ClaimTypes.Role, "ROLE_ADMIN"),
            new("role", "ROLE_ADMIN")
        };
        _controller.ControllerContext.HttpContext.User = new ClaimsPrincipal(new ClaimsIdentity(claims, "TestAuth"));
    }

    [Fact]
    public async Task CreateUser_Returns201Created_WithUserResponse()
    {
        SetAdminContext();
        var request = new AdminCreateUserRequest
        {
            FirstName = "Suresh",
            LastName = "Patel",
            Email = "suresh@example.com",
            PhoneNumber = "+919876543999",
            Password = "Password123!",
            Role = UserRole.RIDER
        };

        var expectedUser = new UserResponse
        {
            Uuid = "new-user-uuid-999",
            FirstName = "Suresh",
            LastName = "Patel",
            Email = "suresh@example.com",
            Role = UserRole.RIDER,
            Status = UserStatus.ACTIVE
        };

        _userServiceMock.Setup(s => s.CreateUserAsync(request, It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedUser);

        var result = await _controller.CreateUser(request, CancellationToken.None);

        var createdResult = Assert.IsType<CreatedAtActionResult>(result.Result);
        Assert.Equal(StatusCodes.Status201Created, createdResult.StatusCode);
        var actualUser = Assert.IsType<UserResponse>(createdResult.Value);
        Assert.Equal("new-user-uuid-999", actualUser.Uuid);
    }

    [Fact]
    public async Task GetUsers_Returns200Ok_WithPagedResponse()
    {
        SetAdminContext();
        var pagedResponse = new PagedResponse<UserResponse>
        {
            Content = new List<UserResponse>
            {
                new() { Uuid = "u1", FirstName = "John", Email = "john@example.com", Role = UserRole.RIDER }
            },
            Page = 0,
            Size = 20,
            TotalElements = 1,
            TotalPages = 1,
            Last = true
        };

        _userServiceMock.Setup(s => s.GetUsersAsync(UserRole.RIDER, null, null, 0, 20, "createdAt", "desc", It.IsAny<CancellationToken>()))
            .ReturnsAsync(pagedResponse);

        var result = await _controller.GetUsers(UserRole.RIDER, null, null, 0, 20, "createdAt", "desc", CancellationToken.None);

        var okResult = Assert.IsType<OkObjectResult>(result.Result);
        Assert.Equal(StatusCodes.Status200OK, okResult.StatusCode);
        var actual = Assert.IsType<PagedResponse<UserResponse>>(okResult.Value);
        Assert.Single(actual.Content);
    }

    [Fact]
    public async Task GetUserByUuid_Returns200Ok_WithUserResponse()
    {
        SetAdminContext();
        var expectedUser = new UserResponse { Uuid = "target-uuid-123", FirstName = "Vikram", Role = UserRole.DRIVER };

        _userServiceMock.Setup(s => s.GetUserByUuidAsync("target-uuid-123", It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedUser);

        var result = await _controller.GetUserByUuid("target-uuid-123", CancellationToken.None);

        var okResult = Assert.IsType<OkObjectResult>(result.Result);
        Assert.Equal(StatusCodes.Status200OK, okResult.StatusCode);
    }

    [Fact]
    public async Task UpdateUserStatus_Returns200Ok_WithUpdatedStatus()
    {
        SetAdminContext("admin-uuid-001");
        var request = new UpdateUserStatusRequest { Status = UserStatus.SUSPENDED };
        var expectedUser = new UserResponse { Uuid = "target-uuid-123", Status = UserStatus.SUSPENDED };

        _userServiceMock.Setup(s => s.UpdateUserStatusAsync("admin-uuid-001", "target-uuid-123", request, It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedUser);

        var result = await _controller.UpdateUserStatus("target-uuid-123", request, CancellationToken.None);

        var okResult = Assert.IsType<OkObjectResult>(result.Result);
        Assert.Equal(StatusCodes.Status200OK, okResult.StatusCode);
        var actual = Assert.IsType<UserResponse>(okResult.Value);
        Assert.Equal(UserStatus.SUSPENDED, actual.Status);
    }

    [Fact]
    public async Task DeactivateUserByUuid_Returns204NoContent()
    {
        SetAdminContext("admin-uuid-001");
        _userServiceMock.Setup(s => s.DeactivateUserByUuidAsync("admin-uuid-001", "target-uuid-123", It.IsAny<CancellationToken>()))
            .Returns(Task.CompletedTask);

        var result = await _controller.DeactivateUserByUuid("target-uuid-123", CancellationToken.None);

        Assert.IsType<NoContentResult>(result);
    }
}
