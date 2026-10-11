using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using DriverService.Enums;

namespace DriverService.Entities;

[Table("driver_profiles")]
public class DriverProfile
{
    [Key]
    [Column("id")]
    public long Id { get; set; }

    [Column("user_id")]
    public long UserId { get; set; }

    [ForeignKey(nameof(UserId))]
    public User User { get; set; } = null!;

    [Column("license_number")]
    [StringLength(50)]
    public string? LicenseNumber { get; set; }

    [Column("license_expiry_date")]
    public DateOnly? LicenseExpiryDate { get; set; }

    [Column("license_issue_date")]
    public DateOnly? LicenseIssueDate { get; set; }

    [Column("license_issuing_authority")]
    [StringLength(100)]
    public string? LicenseIssuingAuthority { get; set; }

    [Column("license_issuing_state")]
    [StringLength(100)]
    public string? LicenseIssuingState { get; set; }

    [Column("license_issuing_country")]
    [StringLength(100)]
    public string LicenseIssuingCountry { get; set; } = "India";

    [Column("license_class")]
    [StringLength(50)]
    public string LicenseClass { get; set; } = "LMV";

    [Column("date_of_birth")]
    public DateOnly? DateOfBirth { get; set; }

    [Column("residential_address")]
    [StringLength(255)]
    public string? ResidentialAddress { get; set; }

    [Column("city")]
    [StringLength(100)]
    public string? City { get; set; }

    [Column("state")]
    [StringLength(100)]
    public string? State { get; set; }

    [Column("postal_code")]
    [StringLength(20)]
    public string? PostalCode { get; set; }

    [Column("country")]
    [StringLength(100)]
    public string Country { get; set; } = "India";

    [Column("preferred_language")]
    [StringLength(50)]
    public string PreferredLanguage { get; set; } = "English";

    [Column("status")]
    public DriverStatus Status { get; set; } = DriverStatus.OFFLINE;

    [Column("rating")]
    public decimal Rating { get; set; } = 5.00m;

    [Column("total_trips")]
    public int TotalTrips { get; set; } = 0;

    [Column("acceptance_rate")]
    public decimal AcceptanceRate { get; set; } = 100.00m;

    [Column("cancellation_rate")]
    public decimal CancellationRate { get; set; } = 0.00m;

    [Column("is_verified")]
    public bool IsVerified { get; set; } = false;

    [Column("verified_at")]
    public DateTime? VerifiedAt { get; set; }

    [Column("background_check_status")]
    public BackgroundCheckStatus BackgroundCheckStatus { get; set; } = BackgroundCheckStatus.PENDING;

    [Column("onboarding_status")]
    public OnboardingStatus OnboardingStatus { get; set; } = OnboardingStatus.INCOMPLETE;

    [Column("rejection_reason")]
    [StringLength(500)]
    public string? RejectionReason { get; set; }

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
