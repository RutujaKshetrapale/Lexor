using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace BookingService.Entities;

[Table("vehicles")]
public class Vehicle
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
    [Column("driver_id")]
    public long DriverId { get; set; }

    [ForeignKey("DriverId")]
    public DriverProfile Driver { get; set; } = null!;

    [Required]
    [MaxLength(50)]
    [Column("make")]
    public string Make { get; set; } = string.Empty;

    [Required]
    [MaxLength(50)]
    [Column("model")]
    public string Model { get; set; } = string.Empty;

    [Required]
    [Column("year")]
    public int Year { get; set; }

    [Required]
    [MaxLength(30)]
    [Column("color")]
    public string Color { get; set; } = string.Empty;

    [Required]
    [MaxLength(20)]
    [Column("license_plate")]
    public string LicensePlate { get; set; } = string.Empty;

    [MaxLength(17)]
    [Column("vin")]
    public string? Vin { get; set; }

    [Column("seat_capacity")]
    public int SeatCapacity { get; set; } = 4;

    [Column("is_active")]
    public bool IsActive { get; set; } = true;

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
