using System.ComponentModel.DataAnnotations;

namespace VehicleService.DTOs.Request;

public class AssignDriverRequest
{
    [Required(ErrorMessage = "Driver user UUID is required")]
    public string DriverUuid { get; set; } = string.Empty;

    [StringLength(500, ErrorMessage = "Reason must not exceed 500 characters")]
    public string? Reason { get; set; }
}
