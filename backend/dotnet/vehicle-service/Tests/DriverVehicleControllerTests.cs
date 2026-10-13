using System.Security.Claims;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Moq;
using VehicleService.Controllers;
using VehicleService.DTOs.Request;
using VehicleService.DTOs.Response;
using VehicleService.Enums;
using VehicleService.Services;
using Xunit;

namespace VehicleService.Tests;

public class DriverVehicleControllerTests
{
    private readonly Mock<IVehicleService> _mockService;
    private readonly DriverVehicleController _controller;

    public DriverVehicleControllerTests()
    {
        _mockService = new Mock<IVehicleService>();
        _controller = new DriverVehicleController(_mockService.Object);

        var user = new ClaimsPrincipal(new ClaimsIdentity(new[]
        {
            new Claim("sub", "driver-uuid-1"),
            new Claim("userId", "1"),
            new Claim(ClaimTypes.Role, "ROLE_DRIVER"),
            new Claim("role", "ROLE_DRIVER")
        }, "TestAuth"));

        _controller.ControllerContext = new ControllerContext
        {
            HttpContext = new DefaultHttpContext { User = user }
        };
    }

    [Fact]
    public async Task RegisterVehicle_Returns201Created()
    {
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

        var response = new VehicleResponse
        {
            Uuid = "new-uuid",
            LicensePlate = "KA-01-EQ-4829",
            VerificationStatus = VehicleVerificationStatus.PENDING_VERIFICATION
        };

        _mockService.Setup(s => s.RegisterVehicleAsync(It.IsAny<ClaimsPrincipal>(), request))
            .ReturnsAsync(response);

        var result = await _controller.RegisterVehicle(request);

        var objectResult = Assert.IsType<ObjectResult>(result);
        Assert.Equal(StatusCodes.Status201Created, objectResult.StatusCode);
        Assert.Equal(response, objectResult.Value);
    }

    [Fact]
    public async Task GetMyVehicles_Returns200OK()
    {
        var list = new List<VehicleResponse>
        {
            new() { Uuid = "v-1", LicensePlate = "KA-01-EQ-4829" }
        };

        _mockService.Setup(s => s.GetMyVehiclesAsync(It.IsAny<ClaimsPrincipal>()))
            .ReturnsAsync(list);

        var result = await _controller.GetMyVehicles();

        var okResult = Assert.IsType<OkObjectResult>(result);
        Assert.Equal(list, okResult.Value);
    }

    [Fact]
    public async Task GetVehicleByUuid_Returns200OK()
    {
        var response = new VehicleResponse { Uuid = "v-1", LicensePlate = "KA-01-EQ-4829" };

        _mockService.Setup(s => s.GetVehicleByUuidAsync(It.IsAny<ClaimsPrincipal>(), "v-1"))
            .ReturnsAsync(response);

        var result = await _controller.GetVehicleByUuid("v-1");

        var okResult = Assert.IsType<OkObjectResult>(result);
        Assert.Equal(response, okResult.Value);
    }

    [Fact]
    public async Task UpdateVehicle_Returns200OK()
    {
        var request = new UpdateVehicleRequest { Color = "Silver" };
        var response = new VehicleResponse { Uuid = "v-1", Color = "Silver" };

        _mockService.Setup(s => s.UpdateVehicleAsync(It.IsAny<ClaimsPrincipal>(), "v-1", request))
            .ReturnsAsync(response);

        var result = await _controller.UpdateVehicle("v-1", request);

        var okResult = Assert.IsType<OkObjectResult>(result);
        Assert.Equal(response, okResult.Value);
    }

    [Fact]
    public async Task UpdateVehicleStatus_Returns200OK()
    {
        var request = new VehicleStatusUpdateRequest { IsActive = false };
        var response = new VehicleResponse { Uuid = "v-1", IsActive = false };

        _mockService.Setup(s => s.UpdateVehicleStatusAsync(It.IsAny<ClaimsPrincipal>(), "v-1", false))
            .ReturnsAsync(response);

        var result = await _controller.UpdateVehicleStatus("v-1", request);

        var okResult = Assert.IsType<OkObjectResult>(result);
        Assert.Equal(response, okResult.Value);
    }

    [Fact]
    public async Task SubmitForVerification_Returns200OK()
    {
        var response = new VehicleResponse { Uuid = "v-1", VerificationStatus = VehicleVerificationStatus.PENDING_VERIFICATION };

        _mockService.Setup(s => s.SubmitForVerificationAsync(It.IsAny<ClaimsPrincipal>(), "v-1"))
            .ReturnsAsync(response);

        var result = await _controller.SubmitForVerification("v-1");

        var okResult = Assert.IsType<OkObjectResult>(result);
        Assert.Equal(response, okResult.Value);
    }

    [Fact]
    public async Task GetVerificationStatus_Returns200OK()
    {
        var response = new VehicleVerificationStatusResponse
        {
            VehicleUuid = "v-1",
            Status = VehicleVerificationStatus.PENDING_VERIFICATION
        };

        _mockService.Setup(s => s.GetVerificationStatusAsync(It.IsAny<ClaimsPrincipal>(), "v-1"))
            .ReturnsAsync(response);

        var result = await _controller.GetVerificationStatus("v-1");

        var okResult = Assert.IsType<OkObjectResult>(result);
        Assert.Equal(response, okResult.Value);
    }
}
