using System.ComponentModel.DataAnnotations;

namespace BookingService.DTOs.Request;

public class StartTripDto
{
    [StringLength(6, MinimumLength = 4, ErrorMessage = "OTP must be between 4 and 6 characters")]
    public string? Otp { get; set; }
}
