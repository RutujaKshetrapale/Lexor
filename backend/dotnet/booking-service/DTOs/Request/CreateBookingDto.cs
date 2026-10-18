using System.ComponentModel.DataAnnotations;

namespace BookingService.DTOs.Request;

public class CreateBookingDto
{
    [Required(ErrorMessage = "Ride request UUID is mandatory")]
    public string RideRequestUuid { get; set; } = string.Empty;

    public DateTime? ScheduledTime { get; set; }

    public string? IdempotencyKey { get; set; }
}
