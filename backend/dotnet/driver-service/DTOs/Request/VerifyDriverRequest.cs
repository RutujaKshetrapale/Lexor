using System.ComponentModel.DataAnnotations;
using DriverService.Enums;

namespace DriverService.DTOs.Request;

public class VerifyDriverRequest
{
    [Required(ErrorMessage = "Onboarding verification status is required")]
    public OnboardingStatus Status { get; set; }

    [MaxLength(500, ErrorMessage = "Notes cannot exceed 500 characters")]
    public string? Notes { get; set; }
}
