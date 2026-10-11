using System.Security.Claims;
using DriverService.Controllers;
using DriverService.DTOs;
using DriverService.DTOs.Request;
using DriverService.DTOs.Response;
using DriverService.Enums;
using DriverService.Services;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Moq;
using Xunit;

namespace DriverService.Tests;

public class DriverControllersTests
{
    private static ClaimsPrincipal CreateUserPrincipal(string uuid, string role)
    {
        var claims = new List<Claim>
        {
            new(ClaimTypes.NameIdentifier, uuid),
            new("sub", uuid),
            new(ClaimTypes.Role, role),
            new("role", role)
        };
        return new ClaimsPrincipal(new ClaimsIdentity(claims, "TestAuth"));
    }

    [Fact]
    public async Task DriverProfileController_GetCurrentDriverProfile_ReturnsOk()
    {
        var mockService = new Mock<IDriverProfileService>();
        mockService.Setup(s => s.GetDriverProfileAsync("u-123"))
            .ReturnsAsync(new DriverProfileResponse
            {
                DriverId = 1,
                UserUuid = "u-123",
                FirstName = "Vikram",
                LastName = "Singh",
                Status = DriverStatus.OFFLINE
            });

        var controller = new DriverProfileController(mockService.Object)
        {
            ControllerContext = new ControllerContext
            {
                HttpContext = new DefaultHttpContext { User = CreateUserPrincipal("u-123", "ROLE_DRIVER") }
            }
        };

        var result = await controller.GetCurrentDriverProfile();

        var okResult = Assert.IsType<OkObjectResult>(result.Result);
        var response = Assert.IsType<DriverProfileResponse>(okResult.Value);
        Assert.Equal("Vikram", response.FirstName);
    }

    [Fact]
    public async Task DriverProfileController_UpdateAvailability_ReturnsOk()
    {
        var mockService = new Mock<IDriverProfileService>();
        mockService.Setup(s => s.UpdateAvailabilityAsync("u-123", It.IsAny<DriverAvailabilityRequest>()))
            .ReturnsAsync(new DriverAvailabilityResponse
            {
                DriverUuid = "u-123",
                Status = DriverStatus.ONLINE,
                IsOnline = true,
                Message = "Driver availability updated to ONLINE"
            });

        var controller = new DriverProfileController(mockService.Object)
        {
            ControllerContext = new ControllerContext
            {
                HttpContext = new DefaultHttpContext { User = CreateUserPrincipal("u-123", "ROLE_DRIVER") }
            }
        };

        var request = new DriverAvailabilityRequest { IsOnline = true };
        var result = await controller.UpdateAvailability(request);

        var okResult = Assert.IsType<OkObjectResult>(result.Result);
        var response = Assert.IsType<DriverAvailabilityResponse>(okResult.Value);
        Assert.True(response.IsOnline);
    }

    [Fact]
    public async Task DriverDocumentController_GetDriverDocuments_ReturnsOk()
    {
        var mockService = new Mock<IDriverDocumentService>();
        mockService.Setup(s => s.GetDriverDocumentsAsync("u-123"))
            .ReturnsAsync(new List<DocumentResponse>
            {
                new() { Id = 1, DocumentType = DocumentType.DRIVING_LICENSE, FileName = "license.pdf" }
            });

        var controller = new DriverDocumentController(mockService.Object)
        {
            ControllerContext = new ControllerContext
            {
                HttpContext = new DefaultHttpContext { User = CreateUserPrincipal("u-123", "ROLE_DRIVER") }
            }
        };

        var result = await controller.GetDriverDocuments();

        var okResult = Assert.IsType<OkObjectResult>(result.Result);
        var response = Assert.IsType<List<DocumentResponse>>(okResult.Value);
        Assert.Single(response);
    }

    [Fact]
    public async Task AdminDriverController_ListDrivers_ReturnsOk()
    {
        var mockService = new Mock<IDriverVerificationService>();
        mockService.Setup(s => s.ListDriversAsync(null, null, null, null, 0, 10, "createdAt", "desc"))
            .ReturnsAsync(new PagedResponse<DriverProfileResponse>
            {
                Content = new List<DriverProfileResponse> { new() { DriverId = 1, FirstName = "Vikram" } },
                Page = 0,
                Size = 10,
                TotalElements = 1,
                TotalPages = 1,
                Last = true
            });

        var controller = new AdminDriverController(mockService.Object)
        {
            ControllerContext = new ControllerContext
            {
                HttpContext = new DefaultHttpContext { User = CreateUserPrincipal("admin-uuid", "ROLE_ADMIN") }
            }
        };

        var result = await controller.ListDrivers(null, null, null, null, 0, 10, "createdAt", "desc");

        var okResult = Assert.IsType<OkObjectResult>(result.Result);
        var response = Assert.IsType<PagedResponse<DriverProfileResponse>>(okResult.Value);
        Assert.Single(response.Content);
    }
}
