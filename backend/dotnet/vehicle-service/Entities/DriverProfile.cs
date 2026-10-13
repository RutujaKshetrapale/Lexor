using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using VehicleService.Enums;

namespace VehicleService.Entities;

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
    public virtual User User { get; set; } = null!;

    [Required]
    [MaxLength(50)]
    [Column("license_number")]
    public string LicenseNumber { get; set; } = string.Empty;

    [Column("license_expiry_date")]
    public DateOnly? LicenseExpiryDate { get; set; }

    [Column("rating")]
    public decimal Rating { get; set; } = 5.00m;

    [Column("total_trips")]
    public int TotalTrips { get; set; }

    [Column("acceptance_rate")]
    public decimal AcceptanceRate { get; set; } = 100.00m;

    [Column("cancellation_rate")]
    public decimal CancellationRate { get; set; } = 0.00m;

    [Required]
    [Column("status")]
    public DriverStatus Status { get; set; } = DriverStatus.OFFLINE;

    [Column("is_verified")]
    public bool IsVerified { get; set; }

    [Required]
    [Column("onboarding_status")]
    public OnboardingStatus OnboardingStatus { get; set; } = OnboardingStatus.INCOMPLETE;

    [Required]
    [Column("background_check_status")]
    public BackgroundCheckStatus BackgroundCheckStatus { get; set; } = BackgroundCheckStatus.PENDING;

    [Column("current_latitude")]
    public decimal? CurrentLatitude { get; set; }

    [Column("current_longitude")]
    public decimal? CurrentLongitude { get; set; }

    [Column("last_location_update")]
    public DateTime? LastLocationUpdate { get; set; }

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
