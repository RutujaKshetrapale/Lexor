using System.ComponentModel.DataAnnotations;

namespace RideRequestService.DTOs.Request;

public class CancelRideRequestDto
{
    [MaxLength(500, ErrorMessage = "Cancellation reason cannot exceed 500 characters")]
    public string? Reason { get; set; }
}
