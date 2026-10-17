using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using DriverMatchingService.Enums;

namespace DriverMatchingService.Entities;

[Table("vehicles")]
public class Vehicle
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [StringLength(36)]
    [Column("uuid")]
    public string Uuid { get; set; } = string.Empty;

    [Required]
    [Column("driver_id")]
    public long DriverId { get; set; }

    [ForeignKey("DriverId")]
    public DriverProfile? Driver { get; set; }

    [Required]
    [StringLength(50)]
    [Column("make")]
    public string Make { get; set; } = string.Empty;

    [Required]
    [StringLength(50)]
    [Column("model")]
    public string Model { get; set; } = string.Empty;

    [Column("year")]
    public int Year { get; set; }

    [Required]
    [StringLength(30)]
    [Column("color")]
    public string Color { get; set; } = string.Empty;

    [Required]
    [StringLength(20)]
    [Column("license_plate")]
    public string LicensePlate { get; set; } = string.Empty;

    [StringLength(17)]
    [Column("vin")]
    public string? Vin { get; set; }

    [Required]
    [StringLength(30)]
    [Column("ride_type_category")]
    public string RideTypeCategory { get; set; } = string.Empty;

    [Column("seat_capacity")]
    public int SeatCapacity { get; set; } = 4;

    [Required]
    [Column("fuel_type")]
    public FuelType FuelType { get; set; } = FuelType.PETROL;

    [Column("is_active")]
    public bool IsActive { get; set; } = true;

    [Column("is_inspected")]
    public bool IsInspected { get; set; } = true;

    [Column("inspection_expires_at")]
    public DateTime? InspectionExpiresAt { get; set; }

    [Required]
    [Column("verification_status")]
    public VehicleVerificationStatus VerificationStatus { get; set; } = VehicleVerificationStatus.PENDING_VERIFICATION;

    [StringLength(500)]
    [Column("rejection_reason")]
    public string? RejectionReason { get; set; }

    [Column("verified_at")]
    public DateTime? VerifiedAt { get; set; }

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    public bool IsEligibleForMatching(string? requestedCategoryCode)
    {
        if (!IsActive || VerificationStatus != VehicleVerificationStatus.VERIFIED || !IsInspected)
        {
            return false;
        }
        if (InspectionExpiresAt.HasValue && InspectionExpiresAt.Value < DateTime.UtcNow.Date)
        {
            return false;
        }
        if (string.IsNullOrWhiteSpace(requestedCategoryCode))
        {
            return true;
        }
        return string.Equals(RideTypeCategory, requestedCategoryCode, StringComparison.OrdinalIgnoreCase);
    }
}
