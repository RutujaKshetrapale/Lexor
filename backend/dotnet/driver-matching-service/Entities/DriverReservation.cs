using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using DriverMatchingService.Enums;

namespace DriverMatchingService.Entities;

[Table("driver_reservations")]
public class DriverReservation
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [StringLength(36)]
    [Column("reservation_uuid")]
    public string ReservationUuid { get; set; } = string.Empty;

    [Required]
    [Column("driver_id")]
    public long DriverId { get; set; }

    [ForeignKey("DriverId")]
    public DriverProfile? Driver { get; set; }

    [Required]
    [Column("ride_request_id")]
    public long RideRequestId { get; set; }

    [ForeignKey("RideRequestId")]
    public RideRequest? RideRequest { get; set; }

    [Required]
    [Column("matching_attempt_id")]
    public long MatchingAttemptId { get; set; }

    [ForeignKey("MatchingAttemptId")]
    public MatchingAttempt? MatchingAttempt { get; set; }

    [Required]
    [Column("status")]
    public ReservationStatus Status { get; set; } = ReservationStatus.RESERVED;

    [Column("expires_at")]
    public DateTime ExpiresAt { get; set; }

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    public bool IsActive()
    {
        return Status == ReservationStatus.RESERVED && ExpiresAt > DateTime.UtcNow;
    }
}
