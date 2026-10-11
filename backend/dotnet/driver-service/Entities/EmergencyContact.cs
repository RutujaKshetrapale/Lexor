using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace DriverService.Entities;

[Table("emergency_contacts")]
public class EmergencyContact
{
    [Key]
    [Column("id")]
    public long Id { get; set; }

    [Column("user_id")]
    public long UserId { get; set; }

    [ForeignKey(nameof(UserId))]
    public User User { get; set; } = null!;

    [Required]
    [Column("contact_name")]
    [StringLength(100)]
    public string ContactName { get; set; } = string.Empty;

    [Required]
    [Column("contact_phone")]
    [StringLength(20)]
    public string ContactPhone { get; set; } = string.Empty;

    [Column("relationship")]
    [StringLength(50)]
    public string? Relationship { get; set; }

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
