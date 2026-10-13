using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using VehicleService.Enums;

namespace VehicleService.Entities;

[Table("vehicles")]
public class Vehicle
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [MaxLength(36)]
    [Column("uuid")]
    public string Uuid { get; set; } = string.Empty;

    [Required]
    [Column("driver_id")]
    public long DriverId { get; set; }

    [ForeignKey("DriverId")]
    public virtual DriverProfile Driver { get; set; } = null!;

    [Required]
    [MaxLength(50)]
    [Column("make")]
    public string Make { get; set; } = string.Empty;

    [Required]
    [MaxLength(50)]
    [Column("model")]
    public string Model { get; set; } = string.Empty;

    [Required]
    [Column("year")]
    public int Year { get; set; }

    [Required]
    [MaxLength(30)]
    [Column("color")]
    public string Color { get; set; } = string.Empty;

    [Required]
    [MaxLength(20)]
    [Column("license_plate")]
    public string LicensePlate { get; set; } = string.Empty;

    [MaxLength(17)]
    [Column("vin")]
    public string? Vin { get; set; }

    [Required]
    [MaxLength(30)]
    [Column("ride_type_category")]
    public string RideTypeCategory { get; set; } = string.Empty;

    [Required]
    [Column("seat_capacity")]
    public int SeatCapacity { get; set; } = 4;

    [Required]
    [Column("fuel_type")]
    public FuelType FuelType { get; set; } = FuelType.PETROL;

    [Column("is_active")]
    public bool IsActive { get; set; } = true;

    [Column("is_inspected")]
    public bool IsInspected { get; set; }

    [Column("inspection_expires_at")]
    public DateOnly? InspectionExpiresAt { get; set; }

    [Required]
    [Column("verification_status")]
    public VehicleVerificationStatus VerificationStatus { get; set; } = VehicleVerificationStatus.PENDING_VERIFICATION;

    [MaxLength(500)]
    [Column("rejection_reason")]
    public string? RejectionReason { get; set; }

    [Column("reviewed_by_admin_id")]
    public long? ReviewedByAdminId { get; set; }

    [ForeignKey("ReviewedByAdminId")]
    public virtual User? ReviewedByAdmin { get; set; }

    [Column("verified_at")]
    public DateTime? VerifiedAt { get; set; }

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
