using System.ComponentModel.DataAnnotations;

namespace DriverService.DTOs.Request;

public class DriverSuspensionRequest
{
    [Required(ErrorMessage = "Suspension flag is required")]
    public bool? Suspended { get; set; }

    [Required(ErrorMessage = "Reason is required for suspension decision")]
    [MaxLength(500, ErrorMessage = "Reason cannot exceed 500 characters")]
    public string Reason { get; set; } = string.Empty;
}
