using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace AdminOperationsService.Entities;

[Table("admin_audit_logs")]
public class AdminAuditLog
{
    [Key]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [MaxLength(36)]
    [Column("audit_uuid")]
    public string AuditUuid { get; set; } = Guid.NewGuid().ToString();

    [Required]
    [MaxLength(36)]
    [Column("admin_user_uuid")]
    public string AdminUserUuid { get; set; } = string.Empty;

    [Required]
    [MaxLength(64)]
    [Column("action")]
    public string Action { get; set; } = string.Empty;

    [Required]
    [MaxLength(64)]
    [Column("resource_type")]
    public string ResourceType { get; set; } = string.Empty;

    [MaxLength(36)]
    [Column("resource_uuid")]
    public string? ResourceUuid { get; set; }

    [MaxLength(500)]
    [Column("reason")]
    public string? Reason { get; set; }

    [Column("details", TypeName = "json")]
    public string? Details { get; set; }

    [MaxLength(45)]
    [Column("ip_address")]
    public string? IpAddress { get; set; }

    [Required]
    [MaxLength(32)]
    [Column("status")]
    public string Status { get; set; } = "SUCCESS";

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
