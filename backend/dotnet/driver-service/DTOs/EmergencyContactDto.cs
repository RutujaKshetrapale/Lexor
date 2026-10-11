using System.ComponentModel.DataAnnotations;

namespace DriverService.DTOs;

public class EmergencyContactDto
{
    [Required(ErrorMessage = "Emergency contact name is required")]
    [MaxLength(100, ErrorMessage = "Emergency contact name cannot exceed 100 characters")]
    public string ContactName { get; set; } = string.Empty;

    [Required(ErrorMessage = "Emergency contact phone is required")]
    [RegularExpression(@"^\+?[0-9]{10,15}$", ErrorMessage = "Emergency contact phone must be valid with 10 to 15 digits")]
    public string ContactPhone { get; set; } = string.Empty;

    [MaxLength(50, ErrorMessage = "Relationship cannot exceed 50 characters")]
    public string? Relationship { get; set; }
}
