using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace RideRequestService.Entities;

[Table("ride_types")]
public class RideType
{
    [Key]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [MaxLength(50)]
    [Column("name")]
    public string Name { get; set; } = string.Empty;

    [Required]
    [MaxLength(30)]
    [Column("code")]
    public string Code { get; set; } = string.Empty;

    [Column("description")]
    public string? Description { get; set; }

    [Column("base_fare")]
    public decimal BaseFare { get; set; }

    [Column("per_km_rate")]
    public decimal PerKmRate { get; set; }

    [Column("per_minute_rate")]
    public decimal PerMinuteRate { get; set; }

    [Column("minimum_fare")]
    public decimal MinimumFare { get; set; }

    [Column("cancellation_fee")]
    public decimal CancellationFee { get; set; }

    [Column("capacity")]
    public int Capacity { get; set; } = 4;

    [MaxLength(500)]
    [Column("icon_url")]
    public string? IconUrl { get; set; }

    [Column("is_active")]
    public bool IsActive { get; set; } = true;

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
