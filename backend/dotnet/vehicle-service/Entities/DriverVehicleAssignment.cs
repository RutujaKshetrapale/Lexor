using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using VehicleService.Enums;

namespace VehicleService.Entities;

[Table("driver_vehicle_assignments")]
public class DriverVehicleAssignment
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [Column("vehicle_id")]
    public long VehicleId { get; set; }

    [ForeignKey("VehicleId")]
    public virtual Vehicle Vehicle { get; set; } = null!;

    [Required]
    [Column("driver_id")]
    public long DriverId { get; set; }

    [ForeignKey("DriverId")]
    public virtual DriverProfile Driver { get; set; } = null!;

    [Column("assigned_by_admin_id")]
    public long? AssignedByAdminId { get; set; }

    [ForeignKey("AssignedByAdminId")]
    public virtual User? AssignedByAdmin { get; set; }

    [Required]
    [Column("status")]
    public AssignmentStatus Status { get; set; } = AssignmentStatus.ACTIVE;

    [Column("assigned_at")]
    public DateTime AssignedAt { get; set; } = DateTime.UtcNow;

    [Column("unassigned_at")]
    public DateTime? UnassignedAt { get; set; }

    [MaxLength(500)]
    [Column("reason")]
    public string? Reason { get; set; }

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
