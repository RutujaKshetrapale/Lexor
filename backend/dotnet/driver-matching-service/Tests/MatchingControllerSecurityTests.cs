using System.Security.Claims;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;
using Moq;
using Xunit;
using DriverMatchingService.Configuration;
using DriverMatchingService.Controllers;
using DriverMatchingService.DTOs.Request;
using DriverMatchingService.DTOs.Response;
using DriverMatchingService.Enums;
using DriverMatchingService.Exceptions;
using DriverMatchingService.Services;

namespace DriverMatchingService.Tests;

public class MatchingControllerSecurityTests
{
    private readonly Mock<IDriverMatchingService> _matchingServiceMock = new();
    private readonly Mock<IDriverLocationService> _locationServiceMock = new();
    private readonly IOptions<MatchingSettings> _matchingSettings = Options.Create(new MatchingSettings
    {
        InternalApiKey = "test-internal-key-123"
    });

    [Fact]
    public async Task InternalMatching_Unauthenticated_ThrowsUnauthorized()
    {
        var controller = new InternalMatchingController(_matchingServiceMock.Object, _matchingSettings)
        {
            ControllerContext = new ControllerContext
            {
                HttpContext = new DefaultHttpContext() // Unauthenticated
            }
        };

        await Assert.ThrowsAsync<UnauthorizedException>(() =>
            controller.InitiateMatching("req-123", null));
    }

    [Fact]
    public async Task InternalMatching_RiderRole_ThrowsForbidden()
    {
        var httpContext = new DefaultHttpContext();
        var claims = new[]
        {
            new Claim(ClaimTypes.NameIdentifier, "rider-uuid"),
            new Claim(ClaimTypes.Role, "RIDER")
        };
        httpContext.User = new ClaimsPrincipal(new ClaimsIdentity(claims, "TestAuth"));

        var controller = new InternalMatchingController(_matchingServiceMock.Object, _matchingSettings)
        {
            ControllerContext = new ControllerContext { HttpContext = httpContext }
        };

        await Assert.ThrowsAsync<ForbiddenException>(() =>
            controller.InitiateMatching("req-123", null));
    }

    [Fact]
    public async Task InternalMatching_AdminRole_Succeeds()
    {
        var httpContext = new DefaultHttpContext();
        var claims = new[]
        {
            new Claim(ClaimTypes.NameIdentifier, "admin-uuid"),
            new Claim(ClaimTypes.Role, "ADMIN")
        };
        httpContext.User = new ClaimsPrincipal(new ClaimsIdentity(claims, "TestAuth"));

        var expectedResponse = new MatchingAttemptResponse
        {
            AttemptUuid = "attempt-1",
            RideRequestUuid = "req-123",
            Status = MatchingStatus.MATCHED
        };

        _matchingServiceMock.Setup(m => m.InitiateMatchingAsync("req-123", null))
            .ReturnsAsync(expectedResponse);

        var controller = new InternalMatchingController(_matchingServiceMock.Object, _matchingSettings)
        {
            ControllerContext = new ControllerContext { HttpContext = httpContext }
        };

        var result = await controller.InitiateMatching("req-123", null);

        var okResult = Assert.IsType<OkObjectResult>(result.Result);
        var response = Assert.IsType<MatchingAttemptResponse>(okResult.Value);
        Assert.Equal("attempt-1", response.AttemptUuid);
    }

    [Fact]
    public async Task InternalMatching_ValidInternalApiKey_Succeeds()
    {
        var httpContext = new DefaultHttpContext();
        httpContext.Request.Headers["X-Internal-Service-Key"] = "test-internal-key-123";

        var expectedResponse = new MatchingAttemptResponse
        {
            AttemptUuid = "attempt-svc-1",
            RideRequestUuid = "req-123",
            Status = MatchingStatus.MATCHED
        };

        _matchingServiceMock.Setup(m => m.InitiateMatchingAsync("req-123", null))
            .ReturnsAsync(expectedResponse);

        var controller = new InternalMatchingController(_matchingServiceMock.Object, _matchingSettings)
        {
            ControllerContext = new ControllerContext { HttpContext = httpContext }
        };

        var result = await controller.InitiateMatching("req-123", null);

        var okResult = Assert.IsType<OkObjectResult>(result.Result);
        var response = Assert.IsType<MatchingAttemptResponse>(okResult.Value);
        Assert.Equal("attempt-svc-1", response.AttemptUuid);
    }

    [Fact]
    public async Task DriverLocation_AuthenticatedDriver_Succeeds()
    {
        var httpContext = new DefaultHttpContext();
        var claims = new[]
        {
            new Claim(ClaimTypes.NameIdentifier, "driver-uuid-123"),
            new Claim(ClaimTypes.Role, "DRIVER")
        };
        httpContext.User = new ClaimsPrincipal(new ClaimsIdentity(claims, "TestAuth"));

        var request = new LocationUpdateRequestDto
        {
            Latitude = 12.97159870m,
            Longitude = 77.59456270m
        };

        var expectedResponse = new LocationUpdateResponse
        {
            DriverUuid = "driver-uuid-123",
            Latitude = request.Latitude,
            Longitude = request.Longitude,
            Status = "SUCCESS"
        };

        _locationServiceMock.Setup(l => l.UpdateDriverLocationAsync("driver-uuid-123", request))
            .ReturnsAsync(expectedResponse);

        var controller = new DriverLocationController(_locationServiceMock.Object)
        {
            ControllerContext = new ControllerContext { HttpContext = httpContext }
        };

        var result = await controller.UpdateLocation(request);

        var okResult = Assert.IsType<OkObjectResult>(result.Result);
        var response = Assert.IsType<LocationUpdateResponse>(okResult.Value);
        Assert.Equal("SUCCESS", response.Status);
        Assert.Equal("driver-uuid-123", response.DriverUuid);
    }
}
