using System.ComponentModel.DataAnnotations;

namespace UserService.DTOs.Request;

public class AdminPatchUserRequest
{
    [StringLength(50, ErrorMessage = "First name cannot exceed 50 characters")]
    public string? FirstName { get; set; }

    [StringLength(50, ErrorMessage = "Last name cannot exceed 50 characters")]
    public string? LastName { get; set; }

    [EmailAddress(ErrorMessage = "Email must be a valid email address")]
    [StringLength(100, ErrorMessage = "Email cannot exceed 100 characters")]
    public string? Email { get; set; }

    [StringLength(20, ErrorMessage = "Phone number cannot exceed 20 characters")]
    public string? PhoneNumber { get; set; }

    [StringLength(500, ErrorMessage = "Profile picture URL cannot exceed 500 characters")]
    public string? ProfilePictureUrl { get; set; }
}
