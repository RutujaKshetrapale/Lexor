using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace RideTypeService.Entities;

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
    [MaxLength(30)]
    [Column("code")]
    public string Code { get; set; } = string.Empty;

    [Column("description")]
    public string? Description { get; set; }

    [Required]
    [Column("base_fare", TypeName = "decimal(10,2)")]
    public decimal BaseFare { get; set; }

    [Required]
    [Column("per_km_rate", TypeName = "decimal(10,2)")]
    public decimal PerKmRate { get; set; }

    [Required]
    [Column("per_minute_rate", TypeName = "decimal(10,2)")]
    public decimal PerMinuteRate { get; set; }

    [Required]
    [Column("minimum_fare", TypeName = "decimal(10,2)")]
    public decimal MinimumFare { get; set; }

    [Required]
    [Column("cancellation_fee", TypeName = "decimal(10,2)")]
    public decimal CancellationFee { get; set; } = 0.00m;

    [Required]
    [Column("capacity")]
    public int Capacity { get; set; } = 4;

    [MaxLength(500)]
    [Column("icon_url")]
    public string? IconUrl { get; set; }

    [Required]
    [Column("is_active")]
    public bool IsActive { get; set; } = true;

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
