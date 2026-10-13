using System.ComponentModel.DataAnnotations;

namespace VehicleService.DTOs.Request;

public class SuspendVehicleRequest
{
    [Required(ErrorMessage = "Action is required ('SUSPEND' or 'REINSTATE')")]
    public string Action { get; set; } = string.Empty;

    [Required(ErrorMessage = "Reason is required")]
    [StringLength(500, ErrorMessage = "Reason must not exceed 500 characters")]
    public string Reason { get; set; } = string.Empty;
}
