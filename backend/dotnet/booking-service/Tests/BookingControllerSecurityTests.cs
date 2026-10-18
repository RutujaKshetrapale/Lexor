using System.Security.Claims;
using BookingService.Controllers;
using BookingService.DTOs.Request;
using BookingService.DTOs.Response;
using BookingService.Enums;
using BookingService.Exceptions;
using BookingService.Services;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Moq;
using Xunit;

namespace BookingService.Tests;

public class BookingControllerSecurityTests
{
    private readonly Mock<IBookingService> _bookingServiceMock = new();

    private static ControllerContext CreateContext(string? userUuid, string role)
    {
        var httpContext = new DefaultHttpContext();
        if (!string.IsNullOrEmpty(userUuid))
        {
            var claims = new List<Claim>
            {
                new("uuid", userUuid),
                new(ClaimTypes.Role, role),
                new("role", role)
            };
            httpContext.User = new ClaimsPrincipal(new ClaimsIdentity(claims, "TestAuth"));
        }
        return new ControllerContext { HttpContext = httpContext };
    }

    [Fact]
    public async Task RiderController_CreateBooking_MissingUserUuid_ThrowsUnauthorized()
    {
        var controller = new BookingController(_bookingServiceMock.Object)
        {
            ControllerContext = new ControllerContext { HttpContext = new DefaultHttpContext() }
        };

        await Assert.ThrowsAsync<UnauthorizedException>(() =>
            controller.CreateBooking(new CreateBookingDto { RideRequestUuid = "req-1" }));
    }

    [Fact]
    public async Task RiderController_CreateBooking_AuthenticatedRider_Returns201Created()
    {
        var expectedResponse = new BookingResponse
        {
            BookingUuid = "b-123",
            Status = BookingStatus.CONFIRMED,
            Otp = "4829"
        };

        _bookingServiceMock.Setup(s => s.CreateBookingAsync(
            "rider-uuid-1",
            It.IsAny<CreateBookingDto>(),
            It.IsAny<string?>(),
            It.IsAny<string?>()))
            .ReturnsAsync(expectedResponse);

        var controller = new BookingController(_bookingServiceMock.Object)
        {
            ControllerContext = CreateContext("rider-uuid-1", "ROLE_RIDER")
        };

        var result = await controller.CreateBooking(new CreateBookingDto { RideRequestUuid = "req-1" });

        var createdResult = Assert.IsType<ObjectResult>(result.Result);
        Assert.Equal(StatusCodes.Status201Created, createdResult.StatusCode);
        var body = Assert.IsType<BookingResponse>(createdResult.Value);
        Assert.Equal("b-123", body.BookingUuid);
    }

    [Fact]
    public async Task RiderController_GetMyBookings_ReturnsOk()
    {
        var expectedPage = PageResponse<BookingResponse>.Create(
            new List<BookingResponse> { new() { BookingUuid = "b-1" } }, 0, 10, 1);

        _bookingServiceMock.Setup(s => s.GetMyBookingsAsync("rider-uuid-1", null, 0, 10))
            .ReturnsAsync(expectedPage);

        var controller = new BookingController(_bookingServiceMock.Object)
        {
            ControllerContext = CreateContext("rider-uuid-1", "ROLE_RIDER")
        };

        var result = await controller.GetMyBookings();
        var okResult = Assert.IsType<OkObjectResult>(result.Result);
        var body = Assert.IsType<PageResponse<BookingResponse>>(okResult.Value);
        Assert.Single(body.Content);
    }

    [Fact]
    public async Task DriverController_AcceptBooking_ReturnsOk()
    {
        var expected = new BookingResponse { BookingUuid = "b-123", Status = BookingStatus.ACCEPTED };

        _bookingServiceMock.Setup(s => s.AcceptBookingByDriverAsync("driver-uuid-1", "b-123"))
            .ReturnsAsync(expected);

        var controller = new DriverBookingController(_bookingServiceMock.Object)
        {
            ControllerContext = CreateContext("driver-uuid-1", "ROLE_DRIVER")
        };

        var result = await controller.AcceptBooking("b-123");
        var okResult = Assert.IsType<OkObjectResult>(result.Result);
        var body = Assert.IsType<BookingResponse>(okResult.Value);
        Assert.Equal(BookingStatus.ACCEPTED, body.Status);
    }

    [Fact]
    public async Task DriverController_StartTrip_ReturnsOk()
    {
        var expected = new BookingResponse { BookingUuid = "b-123", Status = BookingStatus.IN_PROGRESS };

        _bookingServiceMock.Setup(s => s.StartTripByDriverAsync("driver-uuid-1", "b-123", It.IsAny<StartTripDto?>()))
            .ReturnsAsync(expected);

        var controller = new DriverBookingController(_bookingServiceMock.Object)
        {
            ControllerContext = CreateContext("driver-uuid-1", "ROLE_DRIVER")
        };

        var result = await controller.StartTrip("b-123", new StartTripDto { Otp = "4829" });
        var okResult = Assert.IsType<OkObjectResult>(result.Result);
        var body = Assert.IsType<BookingResponse>(okResult.Value);
        Assert.Equal(BookingStatus.IN_PROGRESS, body.Status);
    }

    [Fact]
    public async Task AdminController_GetAdminBookings_ReturnsOk()
    {
        var expectedPage = PageResponse<BookingResponse>.Create(new List<BookingResponse>(), 0, 10, 0);

        _bookingServiceMock.Setup(s => s.GetAdminBookingsAsync(null, null, null, 0, 10))
            .ReturnsAsync(expectedPage);

        var controller = new AdminBookingController(_bookingServiceMock.Object)
        {
            ControllerContext = CreateContext("admin-uuid-1", "ROLE_ADMIN")
        };

        var result = await controller.GetAdminBookings();
        var okResult = Assert.IsType<OkObjectResult>(result.Result);
        Assert.NotNull(okResult.Value);
    }
}
