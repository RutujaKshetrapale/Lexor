using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace DriverMatchingService.Entities;

[Table("ride_types")]
public class RideType
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
    [StringLength(50)]
    [Column("name")]
    public string Name { get; set; } = string.Empty;

    [Required]
    [StringLength(30)]
    [Column("code")]
    public string Code { get; set; } = string.Empty;

    [Column("description")]
    public string? Description { get; set; }

    [Column("base_fare", TypeName = "decimal(10,2)")]
    public decimal BaseFare { get; set; }

    [Column("per_km_rate", TypeName = "decimal(10,2)")]
    public decimal PerKmRate { get; set; }

    [Column("per_minute_rate", TypeName = "decimal(10,2)")]
    public decimal PerMinuteRate { get; set; }

    [Column("minimum_fare", TypeName = "decimal(10,2)")]
    public decimal MinimumFare { get; set; }

    [Column("capacity")]
    public int Capacity { get; set; } = 4;

    [Column("is_active")]
    public bool IsActive { get; set; } = true;

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
