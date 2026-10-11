using DriverService.Enums;

namespace DriverService.DTOs.Response;

public class DriverProfileResponse
{
    public long DriverId { get; set; }
    public string? UserUuid { get; set; }
    public string? FirstName { get; set; }
    public string? LastName { get; set; }
    public string? Email { get; set; }
    public string? PhoneNumber { get; set; }
    public string? ProfilePictureUrl { get; set; }

    // Driving licence details
    public string? LicenseNumber { get; set; }
    public DateOnly? LicenseExpiryDate { get; set; }
    public DateOnly? LicenseIssueDate { get; set; }
    public string? LicenseIssuingAuthority { get; set; }
    public string? LicenseIssuingState { get; set; }
    public string? LicenseIssuingCountry { get; set; }
    public string? LicenseClass { get; set; }

    // Personal & Address details
    public DateOnly? DateOfBirth { get; set; }
    public string? ResidentialAddress { get; set; }
    public string? City { get; set; }
    public string? State { get; set; }
    public string? PostalCode { get; set; }
    public string? Country { get; set; }
    public string? PreferredLanguage { get; set; }

    // Availability and Operational metrics
    public DriverStatus Status { get; set; }
    public decimal Rating { get; set; }
    public int TotalTrips { get; set; }
    public decimal AcceptanceRate { get; set; }
    public decimal CancellationRate { get; set; }

    // Verification and Compliance
    public bool IsVerified { get; set; }
    public DateTime? VerifiedAt { get; set; }
    public BackgroundCheckStatus BackgroundCheckStatus { get; set; }
    public OnboardingStatus OnboardingStatus { get; set; }
    public string? RejectionReason { get; set; }

    // Emergency Contact
    public EmergencyContactDto? EmergencyContact { get; set; }

    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
}
