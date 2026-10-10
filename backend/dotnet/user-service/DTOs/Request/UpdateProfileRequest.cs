using System.ComponentModel.DataAnnotations;

namespace UserService.DTOs.Request;

public class UpdateProfileRequest
{
    [Required(ErrorMessage = "First name is required")]
    [StringLength(50, ErrorMessage = "First name cannot exceed 50 characters")]
    public string FirstName { get; set; } = string.Empty;

    [Required(ErrorMessage = "Last name is required")]
    [StringLength(50, ErrorMessage = "Last name cannot exceed 50 characters")]
    public string LastName { get; set; } = string.Empty;

    [Required(ErrorMessage = "Phone number is required")]
    [StringLength(20, ErrorMessage = "Phone number cannot exceed 20 characters")]
    public string PhoneNumber { get; set; } = string.Empty;

    [StringLength(500, ErrorMessage = "Profile picture URL cannot exceed 500 characters")]
    public string? ProfilePictureUrl { get; set; }
}
