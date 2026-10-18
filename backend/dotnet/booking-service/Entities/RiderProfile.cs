using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace BookingService.Entities;

[Table("rider_profiles")]
public class RiderProfile
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

    [Column("rating", TypeName = "decimal(3, 2)")]
    public decimal Rating { get; set; } = 5.00m;

    [Column("total_rides")]
    public int TotalRides { get; set; } = 0;

    [MaxLength(20)]
    [Column("preferred_payment_method")]
    public string PreferredPaymentMethod { get; set; } = "UPI";

    [MaxLength(255)]
    [Column("home_address")]
    public string? HomeAddress { get; set; }

    [MaxLength(255)]
    [Column("work_address")]
    public string? WorkAddress { get; set; }

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
