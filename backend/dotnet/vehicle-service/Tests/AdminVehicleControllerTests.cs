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

public class AdminVehicleControllerTests
{
    private readonly Mock<IVehicleAdminService> _mockAdminService;
    private readonly AdminVehicleController _controller;

    public AdminVehicleControllerTests()
    {
        _mockAdminService = new Mock<IVehicleAdminService>();
        _controller = new AdminVehicleController(_mockAdminService.Object);

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
    public async Task ListVehicles_Returns200OK()
    {
        var paged = new PagedResponse<VehicleResponse>
        {
            Content = new List<VehicleResponse> { new() { Uuid = "v-1", LicensePlate = "KA-01-EQ-4829" } },
            Page = 0,
            Size = 10,
            TotalElements = 1,
            TotalPages = 1,
            Last = true
        };

        _mockAdminService.Setup(s => s.ListVehiclesAsync(null, null, null, null, 0, 10, "createdAt", "desc"))
            .ReturnsAsync(paged);

        var result = await _controller.ListVehicles(null, null, null, null, 0, 10, "createdAt", "desc");

        var okResult = Assert.IsType<OkObjectResult>(result);
        Assert.Equal(paged, okResult.Value);
    }

    [Fact]
    public async Task ListPendingVehicles_Returns200OK()
    {
        var paged = new PagedResponse<VehicleResponse>
        {
            Content = new List<VehicleResponse> { new() { Uuid = "v-1", VerificationStatus = VehicleVerificationStatus.PENDING_VERIFICATION } },
            Page = 0,
            Size = 10,
            TotalElements = 1,
            TotalPages = 1,
            Last = true
        };

        _mockAdminService.Setup(s => s.ListPendingVehiclesAsync(0, 10, "createdAt", "asc"))
            .ReturnsAsync(paged);

        var result = await _controller.ListPendingVehicles(0, 10, "createdAt", "asc");

        var okResult = Assert.IsType<OkObjectResult>(result);
        Assert.Equal(paged, okResult.Value);
    }

    [Fact]
    public async Task ReviewVehicle_Returns200OK()
    {
        var request = new ReviewVehicleRequest
        {
            Status = VehicleVerificationStatus.VERIFIED,
            Notes = "Approved"
        };
        var response = new VehicleResponse
        {
            Uuid = "v-1",
            VerificationStatus = VehicleVerificationStatus.VERIFIED,
            IsInspected = true
        };

        _mockAdminService.Setup(s => s.ReviewVehicleAsync(It.IsAny<ClaimsPrincipal>(), "v-1", request))
            .ReturnsAsync(response);

        var result = await _controller.ReviewVehicle("v-1", request);

        var okResult = Assert.IsType<OkObjectResult>(result);
        Assert.Equal(response, okResult.Value);
    }

    [Fact]
    public async Task ReviewDocument_Returns200OK()
    {
        var request = new ReviewVehicleDocumentRequest
        {
            Status = DocumentVerificationStatus.VERIFIED,
            ReviewNotes = "Valid"
        };
        var response = new VehicleDocumentResponse
        {
            Id = 10,
            VerificationStatus = DocumentVerificationStatus.VERIFIED
        };

        _mockAdminService.Setup(s => s.ReviewDocumentAsync(It.IsAny<ClaimsPrincipal>(), "v-1", 10, request))
            .ReturnsAsync(response);

        var result = await _controller.ReviewDocument("v-1", 10, request);

        var okResult = Assert.IsType<OkObjectResult>(result);
        Assert.Equal(response, okResult.Value);
    }

    [Fact]
    public async Task SuspendVehicle_Returns200OK()
    {
        var request = new SuspendVehicleRequest
        {
            Action = "SUSPEND",
            Reason = "Investigation"
        };
        var response = new VehicleResponse
        {
            Uuid = "v-1",
            VerificationStatus = VehicleVerificationStatus.SUSPENDED,
            IsActive = false
        };

        _mockAdminService.Setup(s => s.SuspendOrReinstateVehicleAsync(It.IsAny<ClaimsPrincipal>(), "v-1", request))
            .ReturnsAsync(response);

        var result = await _controller.SuspendOrReinstateVehicle("v-1", request);

        var okResult = Assert.IsType<OkObjectResult>(result);
        Assert.Equal(response, okResult.Value);
    }

    [Fact]
    public async Task AssignDriver_Returns200OK()
    {
        var request = new AssignDriverRequest
        {
            DriverUuid = "driver-uuid-1",
            Reason = "Assignment"
        };
        var response = new DriverVehicleAssignmentResponse
        {
            Id = 5,
            VehicleUuid = "v-1",
            DriverUuid = "driver-uuid-1",
            Status = AssignmentStatus.ACTIVE
        };

        _mockAdminService.Setup(s => s.AssignDriverAsync(It.IsAny<ClaimsPrincipal>(), "v-1", request))
            .ReturnsAsync(response);

        var result = await _controller.AssignDriver("v-1", request);

        var okResult = Assert.IsType<OkObjectResult>(result);
        Assert.Equal(response, okResult.Value);
    }

    [Fact]
    public async Task UnassignDriver_Returns200OK()
    {
        var response = new DriverVehicleAssignmentResponse
        {
            Id = 5,
            VehicleUuid = "v-1",
            Status = AssignmentStatus.TERMINATED
        };

        _mockAdminService.Setup(s => s.UnassignDriverAsync(It.IsAny<ClaimsPrincipal>(), "v-1", 5, "Driver resigned"))
            .ReturnsAsync(response);

        var result = await _controller.UnassignDriver("v-1", 5, "Driver resigned");

        var okResult = Assert.IsType<OkObjectResult>(result);
        Assert.Equal(response, okResult.Value);
    }
}
