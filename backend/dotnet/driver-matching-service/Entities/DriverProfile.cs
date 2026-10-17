using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using DriverMatchingService.Enums;

namespace DriverMatchingService.Entities;

[Table("driver_profiles")]
public class DriverProfile
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [Column("user_id")]
    public long UserId { get; set; }

    [ForeignKey("UserId")]
    public User? User { get; set; }

    [StringLength(50)]
    [Column("license_number")]
    public string? LicenseNumber { get; set; }

    [Column("license_expiry_date")]
    public DateTime? LicenseExpiryDate { get; set; }

    [Required]
    [Column("status")]
    public DriverStatus Status { get; set; } = DriverStatus.OFFLINE;

    [Column("rating", TypeName = "decimal(3,2)")]
    public decimal Rating { get; set; } = 5.00m;

    [Column("total_trips")]
    public int TotalTrips { get; set; }

    [Column("acceptance_rate", TypeName = "decimal(5,2)")]
    public decimal AcceptanceRate { get; set; } = 100.00m;

    [Column("cancellation_rate", TypeName = "decimal(5,2)")]
    public decimal CancellationRate { get; set; } = 0.00m;

    [Column("is_verified")]
    public bool IsVerified { get; set; }

    [Column("verified_at")]
    public DateTime? VerifiedAt { get; set; }

    [Required]
    [Column("background_check_status")]
    public BackgroundCheckStatus BackgroundCheckStatus { get; set; } = BackgroundCheckStatus.PENDING;

    [Required]
    [Column("onboarding_status")]
    public OnboardingStatus OnboardingStatus { get; set; } = OnboardingStatus.INCOMPLETE;

    [StringLength(500)]
    [Column("rejection_reason")]
    public string? RejectionReason { get; set; }

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    public bool IsEligibleForMatching()
    {
        return Status == DriverStatus.ONLINE
            && IsVerified
            && OnboardingStatus == OnboardingStatus.VERIFIED
            && BackgroundCheckStatus == BackgroundCheckStatus.PASSED
            && (User == null || User.Status == UserStatus.ACTIVE);
    }
}
