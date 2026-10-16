using System.ComponentModel.DataAnnotations;
using System.Reflection;
using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Moq;
using RideRequestService.Controllers;
using RideRequestService.DTOs.Request;
using RideRequestService.DTOs.Response;
using RideRequestService.Enums;
using RideRequestService.Services;
using Xunit;

namespace RideRequestService.Tests;

public class ControllerSecurityAndValidationTests
{
    [Fact]
    public void RideRequestController_HasAuthorizeAttributeWithRiderRole()
    {
        var authAttr = typeof(RideRequestController).GetCustomAttribute<AuthorizeAttribute>();
        Assert.NotNull(authAttr);
        Assert.Contains("RIDER", authAttr.Roles);
    }

    [Fact]
    public void AdminRideRequestController_HasAuthorizeAttributeWithAdminRole()
    {
        var authAttr = typeof(AdminRideRequestController).GetCustomAttribute<AuthorizeAttribute>();
        Assert.NotNull(authAttr);
        Assert.Contains("ADMIN", authAttr.Roles);
    }

    [Fact]
    public void CreateRideRequestDto_MissingPickupAddress_FailsValidation()
    {
        var dto = new CreateRideRequestDto
        {
            PickupAddress = "",
            PickupLatitude = 12.9784m,
            PickupLongitude = 77.6408m,
            DropAddress = "Prestige Tech Park",
            DropLatitude = 12.9372m,
            DropLongitude = 77.6949m,
            RideTypeCode = "GO"
        };

        var context = new ValidationContext(dto);
        var results = new List<ValidationResult>();
        var isValid = Validator.TryValidateObject(dto, context, results, true);

        Assert.False(isValid);
        Assert.Contains(results, r => r.ErrorMessage != null && r.ErrorMessage.Contains("Pickup address is required"));
    }

    [Fact]
    public void CreateRideRequestDto_InvalidLatitude_FailsValidation()
    {
        var dto = new CreateRideRequestDto
        {
            PickupAddress = "MG Road",
            PickupLatitude = 95.0m, // > 90.0
            PickupLongitude = 77.6408m,
            DropAddress = "Prestige Tech Park",
            DropLatitude = 12.9372m,
            DropLongitude = 77.6949m,
            RideTypeCode = "GO"
        };

        var context = new ValidationContext(dto);
        var results = new List<ValidationResult>();
        var isValid = Validator.TryValidateObject(dto, context, results, true);

        Assert.False(isValid);
        Assert.Contains(results, r => r.ErrorMessage != null && r.ErrorMessage.Contains("-90.0 and 90.0"));
    }

    [Fact]
    public void CreateRideRequestDto_InvalidLongitude_FailsValidation()
    {
        var dto = new CreateRideRequestDto
        {
            PickupAddress = "MG Road",
            PickupLatitude = 12.9784m,
            PickupLongitude = -185.0m, // < -180.0
            DropAddress = "Prestige Tech Park",
            DropLatitude = 12.9372m,
            DropLongitude = 77.6949m,
            RideTypeCode = "GO"
        };

        var context = new ValidationContext(dto);
        var results = new List<ValidationResult>();
        var isValid = Validator.TryValidateObject(dto, context, results, true);

        Assert.False(isValid);
        Assert.Contains(results, r => r.ErrorMessage != null && r.ErrorMessage.Contains("-180.0 and 180.0"));
    }

    [Fact]
    public async Task RideRequestController_CreateRideRequest_Returns201Created()
    {
        var mockService = new Mock<IRideRequestService>();
        var expectedResponse = new RideRequestResponse
        {
            RequestUuid = "test-uuid-1",
            RideTypeCode = "GO",
            EstimatedFare = 150.00m,
            Status = RideRequestStatus.REQUESTED
        };

        mockService.Setup(s => s.CreateRideRequestAsync(It.IsAny<ClaimsPrincipal>(), It.IsAny<CreateRideRequestDto>(), It.IsAny<string?>()))
            .ReturnsAsync(expectedResponse);

        var controller = new RideRequestController(mockService.Object);

        var requestDto = new CreateRideRequestDto
        {
            PickupAddress = "MG Road",
            PickupLatitude = 12.9784m,
            PickupLongitude = 77.6408m,
            DropAddress = "Prestige Tech Park",
            DropLatitude = 12.9372m,
            DropLongitude = 77.6949m,
            RideTypeCode = "GO"
        };

        var result = await controller.CreateRideRequest(requestDto, "Bearer test-token");

        var objectResult = Assert.IsType<ObjectResult>(result);
        Assert.Equal(201, objectResult.StatusCode);
        Assert.Equal(expectedResponse, objectResult.Value);
    }
}
