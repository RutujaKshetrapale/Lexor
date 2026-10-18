using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using BookingService.Enums;

namespace BookingService.Entities;

[Table("driver_profiles")]
public class DriverProfile
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [Column("user_id")]
    public long UserId { get; set; }

    [ForeignKey("UserId")]
    public User User { get; set; } = null!;

    [MaxLength(50)]
    [Column("license_number")]
    public string? LicenseNumber { get; set; }

    [Column("license_expiry_date")]
    public DateOnly? LicenseExpiryDate { get; set; }

    [Required]
    [Column("status")]
    public DriverStatus Status { get; set; } = DriverStatus.OFFLINE;

    [Column("rating", TypeName = "decimal(3, 2)")]
    public decimal Rating { get; set; } = 5.00m;

    [Column("total_trips")]
    public int TotalTrips { get; set; } = 0;

    [Column("acceptance_rate", TypeName = "decimal(5, 2)")]
    public decimal AcceptanceRate { get; set; } = 100.00m;

    [Column("cancellation_rate", TypeName = "decimal(5, 2)")]
    public decimal CancellationRate { get; set; } = 0.00m;

    [Column("is_verified")]
    public bool IsVerified { get; set; } = false;

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
