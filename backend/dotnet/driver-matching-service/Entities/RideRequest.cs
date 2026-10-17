using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using DriverMatchingService.Enums;

namespace DriverMatchingService.Entities;

[Table("ride_requests")]
public class RideRequest
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
    [Column("rider_id")]
    public long RiderId { get; set; }

    [Required]
    [Column("ride_type_id")]
    public long RideTypeId { get; set; }

    [ForeignKey("RideTypeId")]
    public RideType? RideType { get; set; }

    [Required]
    [StringLength(255)]
    [Column("pickup_address")]
    public string PickupAddress { get; set; } = string.Empty;

    [Column("pickup_latitude", TypeName = "decimal(10,8)")]
    public decimal PickupLatitude { get; set; }

    [Column("pickup_longitude", TypeName = "decimal(11,8)")]
    public decimal PickupLongitude { get; set; }

    [Required]
    [StringLength(255)]
    [Column("drop_address")]
    public string DropAddress { get; set; } = string.Empty;

    [Column("drop_latitude", TypeName = "decimal(10,8)")]
    public decimal DropLatitude { get; set; }

    [Column("drop_longitude", TypeName = "decimal(11,8)")]
    public decimal DropLongitude { get; set; }

    [Column("estimated_distance_km", TypeName = "decimal(8,2)")]
    public decimal EstimatedDistanceKm { get; set; }

    [Column("estimated_duration_minutes")]
    public int EstimatedDurationMinutes { get; set; }

    [Column("estimated_fare", TypeName = "decimal(10,2)")]
    public decimal EstimatedFare { get; set; }

    [Required]
    [Column("status")]
    public RideRequestStatus Status { get; set; } = RideRequestStatus.REQUESTED;

    [StringLength(500)]
    [Column("cancellation_reason")]
    public string? CancellationReason { get; set; }

    [Column("cancelled_at")]
    public DateTime? CancelledAt { get; set; }

    [Column("requested_at")]
    public DateTime RequestedAt { get; set; } = DateTime.UtcNow;

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    public bool IsEligibleForMatching()
    {
        return Status == RideRequestStatus.REQUESTED || Status == RideRequestStatus.SEARCHING;
    }
}
