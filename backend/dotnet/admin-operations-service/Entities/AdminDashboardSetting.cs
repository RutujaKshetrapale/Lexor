using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace AdminOperationsService.Entities;

[Table("admin_dashboard_settings")]
public class AdminDashboardSetting
{
    [Key]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [MaxLength(64)]
    [Column("setting_key")]
    public string SettingKey { get; set; } = string.Empty;

    [Required]
    [MaxLength(255)]
    [Column("setting_value")]
    public string SettingValue { get; set; } = string.Empty;

    [MaxLength(500)]
    [Column("description")]
    public string? Description { get; set; }

    [MaxLength(36)]
    [Column("updated_by_uuid")]
    public string? UpdatedByUuid { get; set; }

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
