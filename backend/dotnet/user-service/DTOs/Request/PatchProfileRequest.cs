using System.ComponentModel.DataAnnotations;

namespace UserService.DTOs.Request;

public class PatchProfileRequest
{
    [StringLength(50, ErrorMessage = "First name cannot exceed 50 characters")]
    public string? FirstName { get; set; }

    [StringLength(50, ErrorMessage = "Last name cannot exceed 50 characters")]
    public string? LastName { get; set; }

    [StringLength(20, ErrorMessage = "Phone number cannot exceed 20 characters")]
    public string? PhoneNumber { get; set; }

    [StringLength(500, ErrorMessage = "Profile picture URL cannot exceed 500 characters")]
    public string? ProfilePictureUrl { get; set; }
}
