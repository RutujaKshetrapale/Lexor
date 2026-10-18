using System.ComponentModel.DataAnnotations;

namespace BookingService.DTOs.Request;

public class CancelBookingDto
{
    [MaxLength(255, ErrorMessage = "Cancellation reason cannot exceed 255 characters")]
    public string? Reason { get; set; }
}
