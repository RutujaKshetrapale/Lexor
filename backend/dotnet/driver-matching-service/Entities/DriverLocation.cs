using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace DriverMatchingService.Entities;

[Table("driver_locations")]
public class DriverLocation
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [Column("driver_id")]
    public long DriverId { get; set; }

    [ForeignKey("DriverId")]
    public DriverProfile? Driver { get; set; }

    [Column("ride_id")]
    public long? RideId { get; set; }

    [Column("latitude", TypeName = "decimal(10,8)")]
    public decimal Latitude { get; set; }

    [Column("longitude", TypeName = "decimal(11,8)")]
    public decimal Longitude { get; set; }

    [Column("heading", TypeName = "decimal(5,2)")]
    public decimal? Heading { get; set; }

    [Column("speed_kmh", TypeName = "decimal(5,2)")]
    public decimal? SpeedKmh { get; set; }

    [Column("recorded_at")]
    public DateTime RecordedAt { get; set; } = DateTime.UtcNow;
}
