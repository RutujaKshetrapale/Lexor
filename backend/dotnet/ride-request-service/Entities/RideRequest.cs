using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using RideRequestService.Enums;

namespace RideRequestService.Entities;

[Table("ride_requests")]
public class RideRequest
{
    [Key]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [MaxLength(36)]
    [Column("uuid")]
    public string Uuid { get; set; } = string.Empty;

    [Column("rider_id")]
    public long RiderId { get; set; }

    [ForeignKey(nameof(RiderId))]
    public RiderProfile Rider { get; set; } = null!;

    [Column("ride_type_id")]
    public long RideTypeId { get; set; }

    [ForeignKey(nameof(RideTypeId))]
    public RideType RideType { get; set; } = null!;

    [Required]
    [MaxLength(255)]
    [Column("pickup_address")]
    public string PickupAddress { get; set; } = string.Empty;

    [Column("pickup_latitude")]
    public decimal PickupLatitude { get; set; }

    [Column("pickup_longitude")]
    public decimal PickupLongitude { get; set; }

    [Required]
    [MaxLength(255)]
    [Column("drop_address")]
    public string DropAddress { get; set; } = string.Empty;

    [Column("drop_latitude")]
    public decimal DropLatitude { get; set; }

    [Column("drop_longitude")]
    public decimal DropLongitude { get; set; }

    [Column("estimated_distance_km")]
    public decimal EstimatedDistanceKm { get; set; }

    [Column("estimated_duration_minutes")]
    public int EstimatedDurationMinutes { get; set; }

    [Column("estimated_fare")]
    public decimal EstimatedFare { get; set; }

    [Column("coupon_id")]
    public long? CouponId { get; set; }

    [Required]
    [Column("status")]
    public RideRequestStatus Status { get; set; } = RideRequestStatus.REQUESTED;

    [Column("requested_at")]
    public DateTime RequestedAt { get; set; } = DateTime.UtcNow;

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
