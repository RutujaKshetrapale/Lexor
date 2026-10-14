using System.Security.Claims;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Moq;
using RideTypeService.Controllers;
using RideTypeService.DTOs.Response;
using RideTypeService.Exceptions;
using RideTypeService.Services;
using Xunit;

namespace RideTypeService.Tests;

public class RideTypeControllerTests
{
    private readonly Mock<IRideTypeService> _mockRideTypeService;
    private readonly RideTypeController _controller;

    public RideTypeControllerTests()
    {
        _mockRideTypeService = new Mock<IRideTypeService>();
        _controller = new RideTypeController(_mockRideTypeService.Object);

        var riderUser = new ClaimsPrincipal(new ClaimsIdentity(new[]
        {
            new Claim("sub", "rider-uuid-111"),
            new Claim("userId", "1"),
            new Claim(ClaimTypes.Role, "ROLE_RIDER"),
            new Claim("role", "ROLE_RIDER")
        }, "TestAuth"));

        _controller.ControllerContext = new ControllerContext
        {
            HttpContext = new DefaultHttpContext { User = riderUser }
        };
    }

    [Fact]
    public async Task GetAvailableRideTypes_Returns200OK_WithEnabledRideTypes()
    {
        var rideTypes = new List<RideTypeResponse>
        {
            new() { Uuid = "uuid-1", Name = "LEXOR Go", Code = "GO", BaseFare = 50, IsActive = true },
            new() { Uuid = "uuid-2", Name = "LEXOR XL", Code = "XL", BaseFare = 90, IsActive = true }
        };

        _mockRideTypeService.Setup(s => s.GetEnabledRideTypesAsync())
            .ReturnsAsync(rideTypes);

        var result = await _controller.GetAvailableRideTypes();

        var okResult = Assert.IsType<OkObjectResult>(result);
        var returnList = Assert.IsType<List<RideTypeResponse>>(okResult.Value);
        Assert.Equal(2, returnList.Count);
        Assert.Equal("GO", returnList[0].Code);
    }

    [Fact]
    public async Task GetRideTypeByUuid_WhenFound_Returns200OK()
    {
        var rideType = new RideTypeResponse
        {
            Uuid = "uuid-1", Name = "LEXOR Go", Code = "GO", BaseFare = 50, IsActive = true
        };

        _mockRideTypeService.Setup(s => s.GetEnabledRideTypeByUuidAsync("uuid-1"))
            .ReturnsAsync(rideType);

        var result = await _controller.GetRideTypeByUuid("uuid-1");

        var okResult = Assert.IsType<OkObjectResult>(result);
        var returnItem = Assert.IsType<RideTypeResponse>(okResult.Value);
        Assert.Equal("uuid-1", returnItem.Uuid);
        Assert.Equal("LEXOR Go", returnItem.Name);
    }

    [Fact]
    public async Task GetRideTypeByUuid_WhenNotFound_ThrowsResourceNotFoundException()
    {
        _mockRideTypeService.Setup(s => s.GetEnabledRideTypeByUuidAsync("missing-uuid"))
            .ThrowsAsync(new ResourceNotFoundException("Enabled ride type not found with UUID: missing-uuid"));

        await Assert.ThrowsAsync<ResourceNotFoundException>(() => _controller.GetRideTypeByUuid("missing-uuid"));
    }
}
