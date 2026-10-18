using System.ComponentModel.DataAnnotations;

namespace BookingService.DTOs.Request;

public class DeclineBookingDto
{
    [MaxLength(255, ErrorMessage = "Decline reason cannot exceed 255 characters")]
    public string? Reason { get; set; }
}
