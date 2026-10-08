using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace AuthService.Entities;

[Table("users")]
public class User
{
    [Key]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [Column("uuid")]
    [StringLength(36)]
    public string Uuid { get; set; } = string.Empty;

    [Required]
    [Column("first_name")]
    [StringLength(50)]
    public string FirstName { get; set; } = string.Empty;

    [Required]
    [Column("last_name")]
    [StringLength(50)]
    public string LastName { get; set; } = string.Empty;

    [Required]
    [Column("email")]
    [StringLength(100)]
    public string Email { get; set; } = string.Empty;

    [Required]
    [Column("phone_number")]
    [StringLength(20)]
    public string PhoneNumber { get; set; } = string.Empty;

    [Required]
    [Column("password_hash")]
    [StringLength(255)]
    public string PasswordHash { get; set; } = string.Empty;

    [Required]
    [Column("role")]
    public UserRole Role { get; set; } = UserRole.RIDER;

    [Required]
    [Column("status")]
    public UserStatus Status { get; set; } = UserStatus.ACTIVE;

    [Column("profile_picture_url")]
    [StringLength(500)]
    public string? ProfilePictureUrl { get; set; }

    [Column("email_verified")]
    public bool EmailVerified { get; set; } = false;

    [Column("phone_verified")]
    public bool PhoneVerified { get; set; } = false;

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
