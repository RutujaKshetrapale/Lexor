using BookingService.DTOs.Request;
using BookingService.DTOs.Response;
using BookingService.Enums;
using BookingService.Exceptions;
using BookingService.Extensions;
using BookingService.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace BookingService.Controllers;

[ApiController]
[Route("api/v1/drivers/me/bookings")]
[Authorize(Roles = "ROLE_DRIVER,DRIVER")]
public class DriverBookingController : ControllerBase
{
    private readonly IBookingService _bookingService;

    public DriverBookingController(IBookingService bookingService)
    {
        _bookingService = bookingService;
    }

    [HttpGet]
    public async Task<ActionResult<PageResponse<BookingResponse>>> GetDriverBookings(
        [FromQuery] BookingStatus? status = null,
        [FromQuery] int page = 0,
        [FromQuery] int size = 10)
    {
        var userUuid = User.GetUserUuid();
        if (string.IsNullOrWhiteSpace(userUuid))
        {
            throw new UnauthorizedException("Authentication token missing required user identifier claim");
        }

        var response = await _bookingService.GetDriverBookingsAsync(userUuid, status, page, size);
        return Ok(response);
    }

    [HttpGet("{bookingUuid}")]
    public async Task<ActionResult<BookingResponse>> GetDriverBookingByUuid([FromRoute] string bookingUuid)
    {
        var userUuid = User.GetUserUuid();
        if (string.IsNullOrWhiteSpace(userUuid))
        {
            throw new UnauthorizedException("Authentication token missing required user identifier claim");
        }

        var response = await _bookingService.GetDriverBookingByUuidAsync(userUuid, bookingUuid);
        return Ok(response);
    }

    [HttpPost("{bookingUuid}/accept")]
    public async Task<ActionResult<BookingResponse>> AcceptBooking([FromRoute] string bookingUuid)
    {
        var userUuid = User.GetUserUuid();
        if (string.IsNullOrWhiteSpace(userUuid))
        {
            throw new UnauthorizedException("Authentication token missing required user identifier claim");
        }

        var response = await _bookingService.AcceptBookingByDriverAsync(userUuid, bookingUuid);
        return Ok(response);
    }

    [HttpPost("{bookingUuid}/decline")]
    public async Task<ActionResult<BookingResponse>> DeclineBooking(
        [FromRoute] string bookingUuid,
        [FromBody] DeclineBookingDto? request)
    {
        var userUuid = User.GetUserUuid();
        if (string.IsNullOrWhiteSpace(userUuid))
        {
            throw new UnauthorizedException("Authentication token missing required user identifier claim");
        }

        var response = await _bookingService.DeclineBookingByDriverAsync(userUuid, bookingUuid, request);
        return Ok(response);
    }

    [HttpPost("{bookingUuid}/arrived")]
    public async Task<ActionResult<BookingResponse>> ArriveBooking([FromRoute] string bookingUuid)
    {
        var userUuid = User.GetUserUuid();
        if (string.IsNullOrWhiteSpace(userUuid))
        {
            throw new UnauthorizedException("Authentication token missing required user identifier claim");
        }

        var response = await _bookingService.ArriveBookingByDriverAsync(userUuid, bookingUuid);
        return Ok(response);
    }

    [HttpPost("{bookingUuid}/start")]
    public async Task<ActionResult<BookingResponse>> StartTrip(
        [FromRoute] string bookingUuid,
        [FromBody] StartTripDto? request)
    {
        var userUuid = User.GetUserUuid();
        if (string.IsNullOrWhiteSpace(userUuid))
        {
            throw new UnauthorizedException("Authentication token missing required user identifier claim");
        }

        var response = await _bookingService.StartTripByDriverAsync(userUuid, bookingUuid, request);
        return Ok(response);
    }

    [HttpPost("{bookingUuid}/complete")]
    public async Task<ActionResult<BookingResponse>> CompleteTrip(
        [FromRoute] string bookingUuid,
        [FromBody] CompleteTripDto? request)
    {
        var userUuid = User.GetUserUuid();
        if (string.IsNullOrWhiteSpace(userUuid))
        {
            throw new UnauthorizedException("Authentication token missing required user identifier claim");
        }

        var response = await _bookingService.CompleteTripByDriverAsync(userUuid, bookingUuid, request);
        return Ok(response);
    }
}
