using System.ComponentModel.DataAnnotations;

namespace VehicleService.DTOs.Request;

public class VehicleStatusUpdateRequest
{
    [Required(ErrorMessage = "isActive flag is required")]
    public bool? IsActive { get; set; }
}
