using System.ComponentModel.DataAnnotations;

namespace DriverService.DTOs.Request;

public class UpdateDriverProfileRequest
{
    [Required(ErrorMessage = "License number is required")]
    [MaxLength(50, ErrorMessage = "License number cannot exceed 50 characters")]
    public string LicenseNumber { get; set; } = string.Empty;

    [Required(ErrorMessage = "License expiry date is required")]
    public DateOnly? LicenseExpiryDate { get; set; }

    public DateOnly? LicenseIssueDate { get; set; }

    [MaxLength(100, ErrorMessage = "Issuing authority cannot exceed 100 characters")]
    public string? LicenseIssuingAuthority { get; set; }

    [MaxLength(100, ErrorMessage = "Issuing state cannot exceed 100 characters")]
    public string? LicenseIssuingState { get; set; }

    [MaxLength(100, ErrorMessage = "Issuing country cannot exceed 100 characters")]
    public string? LicenseIssuingCountry { get; set; }

    [MaxLength(50, ErrorMessage = "License class cannot exceed 50 characters")]
    public string? LicenseClass { get; set; }

    [Required(ErrorMessage = "Date of birth is required")]
    public DateOnly? DateOfBirth { get; set; }

    [Required(ErrorMessage = "Residential address is required")]
    [MaxLength(255, ErrorMessage = "Residential address cannot exceed 255 characters")]
    public string ResidentialAddress { get; set; } = string.Empty;

    [Required(ErrorMessage = "City is required")]
    [MaxLength(100, ErrorMessage = "City cannot exceed 100 characters")]
    public string City { get; set; } = string.Empty;

    [Required(ErrorMessage = "State is required")]
    [MaxLength(100, ErrorMessage = "State cannot exceed 100 characters")]
    public string State { get; set; } = string.Empty;

    [Required(ErrorMessage = "Postal code is required")]
    [MaxLength(20, ErrorMessage = "Postal code cannot exceed 20 characters")]
    public string PostalCode { get; set; } = string.Empty;

    [MaxLength(100, ErrorMessage = "Country cannot exceed 100 characters")]
    public string? Country { get; set; }

    [MaxLength(50, ErrorMessage = "Preferred language cannot exceed 50 characters")]
    public string? PreferredLanguage { get; set; }

    public EmergencyContactDto? EmergencyContact { get; set; }
}
