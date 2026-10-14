using System.Reflection;
using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Moq;
using RideTypeService.Controllers;
using RideTypeService.DTOs.Request;
using RideTypeService.DTOs.Response;
using RideTypeService.Exceptions;
using RideTypeService.Services;
using Xunit;

namespace RideTypeService.Tests;

public class AdminRideTypeControllerTests
{
    private readonly Mock<IRideTypeService> _mockRideTypeService;
    private readonly AdminRideTypeController _controller;

    public AdminRideTypeControllerTests()
    {
        _mockRideTypeService = new Mock<IRideTypeService>();
        _controller = new AdminRideTypeController(_mockRideTypeService.Object);

        var adminUser = new ClaimsPrincipal(new ClaimsIdentity(new[]
        {
            new Claim("sub", "admin-uuid-99"),
            new Claim("userId", "99"),
            new Claim(ClaimTypes.Role, "ROLE_ADMIN"),
            new Claim("role", "ROLE_ADMIN")
        }, "TestAuth"));

        _controller.ControllerContext = new ControllerContext
        {
            HttpContext = new DefaultHttpContext { User = adminUser }
        };
    }

    [Fact]
    public void Controller_HasAuthorizeAttribute_RequiringAdminRole()
    {
        var authAttr = typeof(AdminRideTypeController).GetCustomAttribute<AuthorizeAttribute>();
        Assert.NotNull(authAttr);
        Assert.Contains("ADMIN", authAttr.Roles);
        Assert.Contains("ROLE_ADMIN", authAttr.Roles);
    }

    [Fact]
    public void RiderController_HasAuthorizeAttribute()
    {
        var authAttr = typeof(RideTypeController).GetCustomAttribute<AuthorizeAttribute>();
        Assert.NotNull(authAttr);
    }

    [Fact]
    public async Task CreateRideType_Returns201Created()
    {
        var request = new CreateRideTypeRequest
        {
            Name = "LEXOR Luxury",
            Code = "LUXURY",
            BaseFare = 150,
            PerKmRate = 30,
            PerMinuteRate = 5,
            MinimumFare = 200,
            Capacity = 4
        };

        var response = new RideTypeResponse
        {
            Uuid = "uuid-new",
            Name = "LEXOR Luxury",
            Code = "LUXURY",
            BaseFare = 150,
            IsActive = true
        };

        _mockRideTypeService.Setup(s => s.CreateRideTypeAsync(request))
            .ReturnsAsync(response);

        var result = await _controller.CreateRideType(request);

        var statusCodeResult = Assert.IsType<ObjectResult>(result);
        Assert.Equal(StatusCodes.Status201Created, statusCodeResult.StatusCode);
        var returnItem = Assert.IsType<RideTypeResponse>(statusCodeResult.Value);
        Assert.Equal("uuid-new", returnItem.Uuid);
    }

    [Fact]
    public async Task GetAllRideTypes_Returns200OK()
    {
        var list = new List<RideTypeResponse>
        {
            new() { Uuid = "uuid-1", Name = "Active", IsActive = true },
            new() { Uuid = "uuid-2", Name = "Disabled", IsActive = false }
        };

        _mockRideTypeService.Setup(s => s.GetAllRideTypesAdminAsync())
            .ReturnsAsync(list);

        var result = await _controller.GetAllRideTypes();

        var okResult = Assert.IsType<OkObjectResult>(result);
        var returnList = Assert.IsType<List<RideTypeResponse>>(okResult.Value);
        Assert.Equal(2, returnList.Count);
    }

    [Fact]
    public async Task GetRideTypeByUuid_Returns200OK()
    {
        var response = new RideTypeResponse
        {
            Uuid = "uuid-admin-get",
            Name = "LEXOR Vintage",
            Code = "VINTAGE",
            IsActive = false
        };

        _mockRideTypeService.Setup(s => s.GetRideTypeByUuidAdminAsync("uuid-admin-get"))
            .ReturnsAsync(response);

        var result = await _controller.GetRideTypeByUuid("uuid-admin-get");

        var okResult = Assert.IsType<OkObjectResult>(result);
        var returnItem = Assert.IsType<RideTypeResponse>(okResult.Value);
        Assert.Equal("uuid-admin-get", returnItem.Uuid);
        Assert.False(returnItem.IsActive);
    }

    [Fact]
    public async Task UpdateRideType_Returns200OK()
    {
        var request = new UpdateRideTypeRequest
        {
            Name = "Updated Name",
            BaseFare = 60,
            PerKmRate = 18,
            PerMinuteRate = 3,
            MinimumFare = 90,
            Capacity = 4
        };

        var response = new RideTypeResponse
        {
            Uuid = "uuid-upd",
            Name = "Updated Name",
            BaseFare = 60,
            IsActive = true
        };

        _mockRideTypeService.Setup(s => s.UpdateRideTypeAsync("uuid-upd", request))
            .ReturnsAsync(response);

        var result = await _controller.UpdateRideType("uuid-upd", request);

        var okResult = Assert.IsType<OkObjectResult>(result);
        var returnItem = Assert.IsType<RideTypeResponse>(okResult.Value);
        Assert.Equal("Updated Name", returnItem.Name);
    }

    [Fact]
    public async Task UpdateRideTypeStatus_Returns200OK()
    {
        var request = new UpdateRideTypeStatusRequest { IsActive = false, Reason = "Temporary disable" };
        var response = new RideTypeResponse { Uuid = "uuid-status", IsActive = false };

        _mockRideTypeService.Setup(s => s.UpdateRideTypeStatusAsync("uuid-status", request))
            .ReturnsAsync(response);

        var result = await _controller.UpdateRideTypeStatus("uuid-status", request);

        var okResult = Assert.IsType<OkObjectResult>(result);
        var returnItem = Assert.IsType<RideTypeResponse>(okResult.Value);
        Assert.False(returnItem.IsActive);
    }

    [Fact]
    public async Task DeleteRideType_RejectsHardDeletion_ThrowsBadRequestException()
    {
        _mockRideTypeService.Setup(s => s.DeleteRideTypeSafelyAsync("uuid-del"))
            .ThrowsAsync(new BadRequestException("Hard deletion of ride categories is forbidden to preserve historical records and referential integrity. Please disable the ride type instead."));

        await Assert.ThrowsAsync<BadRequestException>(() => _controller.DeleteRideType("uuid-del"));
    }
}
