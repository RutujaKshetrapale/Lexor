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
[Route("api/v1/bookings")]
[Authorize(Roles = "ROLE_RIDER,RIDER")]
public class BookingController : ControllerBase
{
    private readonly IBookingService _bookingService;

    public BookingController(IBookingService bookingService)
    {
        _bookingService = bookingService;
    }

    [HttpPost]
    public async Task<ActionResult<BookingResponse>> CreateBooking([FromBody] CreateBookingDto request)
    {
        var userUuid = User.GetUserUuid();
        if (string.IsNullOrWhiteSpace(userUuid))
        {
            throw new UnauthorizedException("Authentication token missing required user identifier claim");
        }

        var bearerToken = Request.Headers.Authorization.FirstOrDefault();
        var correlationId = Request.Headers["X-Correlation-Id"].FirstOrDefault();

        var response = await _bookingService.CreateBookingAsync(userUuid, request, bearerToken, correlationId);
        return StatusCode(StatusCodes.Status201Created, response);
    }

    [HttpGet("me")]
    public async Task<ActionResult<PageResponse<BookingResponse>>> GetMyBookings(
        [FromQuery] BookingStatus? status = null,
        [FromQuery] int page = 0,
        [FromQuery] int size = 10)
    {
        var userUuid = User.GetUserUuid();
        if (string.IsNullOrWhiteSpace(userUuid))
        {
            throw new UnauthorizedException("Authentication token missing required user identifier claim");
        }

        var response = await _bookingService.GetMyBookingsAsync(userUuid, status, page, size);
        return Ok(response);
    }

    [HttpGet("me/{bookingUuid}")]
    public async Task<ActionResult<BookingResponse>> GetMyBookingByUuid([FromRoute] string bookingUuid)
    {
        var userUuid = User.GetUserUuid();
        if (string.IsNullOrWhiteSpace(userUuid))
        {
            throw new UnauthorizedException("Authentication token missing required user identifier claim");
        }

        var response = await _bookingService.GetMyBookingByUuidAsync(userUuid, bookingUuid);
        return Ok(response);
    }

    [HttpPost("{bookingUuid}/cancel")]
    public async Task<ActionResult<BookingResponse>> CancelBooking(
        [FromRoute] string bookingUuid,
        [FromBody] CancelBookingDto? request)
    {
        var userUuid = User.GetUserUuid();
        if (string.IsNullOrWhiteSpace(userUuid))
        {
            throw new UnauthorizedException("Authentication token missing required user identifier claim");
        }

        var response = await _bookingService.CancelBookingByRiderAsync(userUuid, bookingUuid, request);
        return Ok(response);
    }
}
