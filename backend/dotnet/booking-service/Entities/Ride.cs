using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using BookingService.Enums;

namespace BookingService.Entities;

[Table("rides")]
public class Ride
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
    [Column("booking_id")]
    public long BookingId { get; set; }

    [ForeignKey("BookingId")]
    public Booking Booking { get; set; } = null!;

    [Required]
    [Column("rider_id")]
    public long RiderId { get; set; }

    [ForeignKey("RiderId")]
    public RiderProfile Rider { get; set; } = null!;

    [Required]
    [Column("driver_id")]
    public long DriverId { get; set; }

    [ForeignKey("DriverId")]
    public DriverProfile Driver { get; set; } = null!;

    [Required]
    [Column("vehicle_id")]
    public long VehicleId { get; set; }

    [ForeignKey("VehicleId")]
    public Vehicle Vehicle { get; set; } = null!;

    [Required]
    [Column("ride_type_id")]
    public long RideTypeId { get; set; }

    [ForeignKey("RideTypeId")]
    public RideType RideType { get; set; } = null!;

    [Required]
    [MaxLength(255)]
    [Column("pickup_address")]
    public string PickupAddress { get; set; } = string.Empty;

    [Required]
    [Column("pickup_latitude", TypeName = "decimal(10, 8)")]
    public decimal PickupLatitude { get; set; }

    [Required]
    [Column("pickup_longitude", TypeName = "decimal(11, 8)")]
    public decimal PickupLongitude { get; set; }

    [Required]
    [MaxLength(255)]
    [Column("drop_address")]
    public string DropAddress { get; set; } = string.Empty;

    [Required]
    [Column("drop_latitude", TypeName = "decimal(10, 8)")]
    public decimal DropLatitude { get; set; }

    [Required]
    [Column("drop_longitude", TypeName = "decimal(11, 8)")]
    public decimal DropLongitude { get; set; }

    [Required]
    [Column("status")]
    public RideStatus Status { get; set; } = RideStatus.REQUESTED;

    [Column("actual_pickup_time")]
    public DateTime? ActualPickupTime { get; set; }

    [Column("actual_drop_time")]
    public DateTime? ActualDropTime { get; set; }

    [Column("actual_distance_km", TypeName = "decimal(8, 2)")]
    public decimal? ActualDistanceKm { get; set; }

    [Column("actual_duration_minutes")]
    public int? ActualDurationMinutes { get; set; }

    [Column("requested_at")]
    public DateTime RequestedAt { get; set; } = DateTime.UtcNow;

    [Column("started_at")]
    public DateTime? StartedAt { get; set; }

    [Column("completed_at")]
    public DateTime? CompletedAt { get; set; }

    [Column("cancelled_at")]
    public DateTime? CancelledAt { get; set; }

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
