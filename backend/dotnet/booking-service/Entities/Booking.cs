using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using BookingService.Enums;

namespace BookingService.Entities;

[Table("bookings")]
public class Booking
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
    [Column("ride_request_id")]
    public long RideRequestId { get; set; }

    [ForeignKey("RideRequestId")]
    public RideRequest RideRequest { get; set; } = null!;

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
    [Column("booking_status")]
    public BookingStatus BookingStatus { get; set; } = BookingStatus.CONFIRMED;

    [Required]
    [MaxLength(255)]
    [Column("otp_hash")]
    public string OtpHash { get; set; } = string.Empty;

    [Column("otp_expires_at")]
    public DateTime? OtpExpiresAt { get; set; }

    [Column("otp_verified_at")]
    public DateTime? OtpVerifiedAt { get; set; }

    [Column("scheduled_time")]
    public DateTime? ScheduledTime { get; set; }

    [MaxLength(255)]
    [Column("cancellation_reason")]
    public string? CancellationReason { get; set; }

    [MaxLength(100)]
    [Column("idempotency_key")]
    public string? IdempotencyKey { get; set; }

    [Column("accepted_at")]
    public DateTime? AcceptedAt { get; set; }

    [Column("arrived_at")]
    public DateTime? ArrivedAt { get; set; }

    [Column("started_at")]
    public DateTime? StartedAt { get; set; }

    [Column("completed_at")]
    public DateTime? CompletedAt { get; set; }

    [Column("cancelled_at")]
    public DateTime? CancelledAt { get; set; }

    [MaxLength(50)]
    [Column("cancelled_by")]
    public string? CancelledBy { get; set; }

    [MaxLength(255)]
    [Column("pickup_address")]
    public string? PickupAddress { get; set; }

    [Column("pickup_latitude", TypeName = "decimal(10, 8)")]
    public decimal? PickupLatitude { get; set; }

    [Column("pickup_longitude", TypeName = "decimal(11, 8)")]
    public decimal? PickupLongitude { get; set; }

    [MaxLength(255)]
    [Column("drop_address")]
    public string? DropAddress { get; set; }

    [Column("drop_latitude", TypeName = "decimal(10, 8)")]
    public decimal? DropLatitude { get; set; }

    [Column("drop_longitude", TypeName = "decimal(11, 8)")]
    public decimal? DropLongitude { get; set; }

    [Column("estimated_distance_km", TypeName = "decimal(8, 2)")]
    public decimal? EstimatedDistanceKm { get; set; }

    [Column("estimated_duration_minutes")]
    public int? EstimatedDurationMinutes { get; set; }

    [Column("total_fare", TypeName = "decimal(10, 2)")]
    public decimal? TotalFare { get; set; }

    [MaxLength(3)]
    [Column("currency")]
    public string Currency { get; set; } = "INR";

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
