using System.ComponentModel.DataAnnotations;

namespace RideTypeService.DTOs.Request;

public class UpdateRideTypeStatusRequest
{
    [Required(ErrorMessage = "isActive status is required")]
    public bool? IsActive { get; set; }

    [StringLength(255, ErrorMessage = "Reason must not exceed 255 characters")]
    public string? Reason { get; set; }
}
