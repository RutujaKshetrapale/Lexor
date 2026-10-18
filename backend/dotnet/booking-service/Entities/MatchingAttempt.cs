using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using BookingService.Enums;

namespace BookingService.Entities;

[Table("matching_attempts")]
public class MatchingAttempt
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [MaxLength(36)]
    [Column("attempt_uuid")]
    public string AttemptUuid { get; set; } = string.Empty;

    [Required]
    [Column("ride_request_id")]
    public long RideRequestId { get; set; }

    [ForeignKey("RideRequestId")]
    public RideRequest RideRequest { get; set; } = null!;

    [Required]
    [MaxLength(36)]
    [Column("ride_request_uuid")]
    public string RideRequestUuid { get; set; } = string.Empty;

    [Column("selected_driver_id")]
    public long? SelectedDriverId { get; set; }

    [ForeignKey("SelectedDriverId")]
    public DriverProfile? SelectedDriver { get; set; }

    [Column("selected_vehicle_id")]
    public long? SelectedVehicleId { get; set; }

    [ForeignKey("SelectedVehicleId")]
    public Vehicle? SelectedVehicle { get; set; }

    [Required]
    [Column("status")]
    public MatchingStatus Status { get; set; } = MatchingStatus.PENDING;

    [Column("attempt_number")]
    public int AttemptNumber { get; set; } = 1;

    [Column("candidates_evaluated")]
    public int CandidatesEvaluated { get; set; } = 0;

    [Column("distance_km", TypeName = "decimal(8, 2)")]
    public decimal? DistanceKm { get; set; }

    [Column("match_score", TypeName = "decimal(5, 2)")]
    public decimal? MatchScore { get; set; }

    [MaxLength(500)]
    [Column("failure_reason")]
    public string? FailureReason { get; set; }

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("completed_at")]
    public DateTime? CompletedAt { get; set; }

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
