using BookingService.DTOs.Response;
using BookingService.Enums;
using BookingService.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace BookingService.Controllers;

[ApiController]
[Route("api/v1/admin/bookings")]
[Authorize(Roles = "ROLE_ADMIN,ADMIN")]
public class AdminBookingController : ControllerBase
{
    private readonly IBookingService _bookingService;

    public AdminBookingController(IBookingService bookingService)
    {
        _bookingService = bookingService;
    }

    [HttpGet]
    public async Task<ActionResult<PageResponse<BookingResponse>>> GetAdminBookings(
        [FromQuery] long? riderId = null,
        [FromQuery] long? driverId = null,
        [FromQuery] BookingStatus? status = null,
        [FromQuery] int page = 0,
        [FromQuery] int size = 10)
    {
        var response = await _bookingService.GetAdminBookingsAsync(riderId, driverId, status, page, size);
        return Ok(response);
    }

    [HttpGet("{bookingUuid}")]
    public async Task<ActionResult<BookingResponse>> GetAdminBookingByUuid([FromRoute] string bookingUuid)
    {
        var response = await _bookingService.GetAdminBookingByUuidAsync(bookingUuid);
        return Ok(response);
    }
}
