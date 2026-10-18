using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace BookingService.Entities;

[Table("ride_types")]
public class RideType
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
    [MaxLength(50)]
    [Column("name")]
    public string Name { get; set; } = string.Empty;

    [Required]
    [MaxLength(20)]
    [Column("code")]
    public string Code { get; set; } = string.Empty;

    [MaxLength(255)]
    [Column("description")]
    public string? Description { get; set; }

    [Column("base_fare", TypeName = "decimal(10, 2)")]
    public decimal BaseFare { get; set; }

    [Column("per_km_rate", TypeName = "decimal(10, 2)")]
    public decimal PerKmRate { get; set; }

    [Column("per_minute_rate", TypeName = "decimal(10, 2)")]
    public decimal PerMinuteRate { get; set; }

    [Column("minimum_fare", TypeName = "decimal(10, 2)")]
    public decimal MinimumFare { get; set; }

    [Column("cancellation_fee", TypeName = "decimal(10, 2)")]
    public decimal CancellationFee { get; set; }

    [Column("capacity")]
    public int Capacity { get; set; }

    [MaxLength(255)]
    [Column("icon_url")]
    public string? IconUrl { get; set; }

    [Column("is_active")]
    public bool IsActive { get; set; } = true;

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
