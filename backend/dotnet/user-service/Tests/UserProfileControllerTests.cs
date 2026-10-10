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

public class UserProfileControllerTests
{
    private readonly Mock<IUserService> _userServiceMock;
    private readonly UserProfileController _controller;

    public UserProfileControllerTests()
    {
        _userServiceMock = new Mock<IUserService>();
        _controller = new UserProfileController(_userServiceMock.Object);

        _controller.ControllerContext = new ControllerContext
        {
            HttpContext = new DefaultHttpContext()
        };
    }

    private void SetUserContext(string uuid, string role = "RIDER")
    {
        var claims = new List<Claim>
        {
            new(ClaimTypes.NameIdentifier, uuid),
            new(ClaimTypes.Role, "ROLE_" + role),
            new("role", "ROLE_" + role)
        };
        _controller.ControllerContext.HttpContext.User = new ClaimsPrincipal(new ClaimsIdentity(claims, "TestAuth"));
    }

    [Fact]
    public async Task GetCurrentUserProfile_Returns200Ok_WithUserResponse()
    {
        SetUserContext("rider-uuid-101");
        var expectedUser = new UserResponse
        {
            Uuid = "rider-uuid-101",
            FirstName = "Rajesh",
            LastName = "Kumar",
            Email = "rajesh@example.com",
            Role = UserRole.RIDER,
            Status = UserStatus.ACTIVE
        };

        _userServiceMock.Setup(s => s.GetCurrentUserProfileAsync("rider-uuid-101", It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedUser);

        var result = await _controller.GetCurrentUserProfile(CancellationToken.None);

        var okResult = Assert.IsType<OkObjectResult>(result.Result);
        Assert.Equal(StatusCodes.Status200OK, okResult.StatusCode);
        var actualUser = Assert.IsType<UserResponse>(okResult.Value);
        Assert.Equal("rider-uuid-101", actualUser.Uuid);
    }

    [Fact]
    public async Task UpdateCurrentUserProfile_Returns200Ok_WhenValid()
    {
        SetUserContext("rider-uuid-101");
        var request = new UpdateProfileRequest
        {
            FirstName = "Rajeshkumar",
            LastName = "Verma",
            PhoneNumber = "+919876543999"
        };

        var expectedUser = new UserResponse
        {
            Uuid = "rider-uuid-101",
            FirstName = "Rajeshkumar",
            LastName = "Verma",
            PhoneNumber = "+919876543999",
            Role = UserRole.RIDER,
            Status = UserStatus.ACTIVE
        };

        _userServiceMock.Setup(s => s.UpdateCurrentUserProfileAsync("rider-uuid-101", request, It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedUser);

        var result = await _controller.UpdateCurrentUserProfile(request, CancellationToken.None);

        var okResult = Assert.IsType<OkObjectResult>(result.Result);
        Assert.Equal(StatusCodes.Status200OK, okResult.StatusCode);
        var actualUser = Assert.IsType<UserResponse>(okResult.Value);
        Assert.Equal("Rajeshkumar", actualUser.FirstName);
    }

    [Fact]
    public async Task PatchCurrentUserProfile_Returns200Ok_WhenValid()
    {
        SetUserContext("rider-uuid-101");
        var request = new PatchProfileRequest
        {
            FirstName = "Raj"
        };

        var expectedUser = new UserResponse
        {
            Uuid = "rider-uuid-101",
            FirstName = "Raj",
            LastName = "Kumar",
            Role = UserRole.RIDER,
            Status = UserStatus.ACTIVE
        };

        _userServiceMock.Setup(s => s.PatchCurrentUserProfileAsync("rider-uuid-101", request, It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedUser);

        var result = await _controller.PatchCurrentUserProfile(request, CancellationToken.None);

        var okResult = Assert.IsType<OkObjectResult>(result.Result);
        Assert.Equal(StatusCodes.Status200OK, okResult.StatusCode);
    }

    [Fact]
    public async Task DeactivateCurrentUser_Returns204NoContent()
    {
        SetUserContext("rider-uuid-101");
        _userServiceMock.Setup(s => s.DeactivateCurrentUserAsync("rider-uuid-101", It.IsAny<CancellationToken>()))
            .Returns(Task.CompletedTask);

        var result = await _controller.DeactivateCurrentUser(CancellationToken.None);

        Assert.IsType<NoContentResult>(result);
    }
}
