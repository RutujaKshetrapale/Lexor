using AdminOperationsService.Entities;
using Microsoft.EntityFrameworkCore;

namespace AdminOperationsService.Data;

public class AdminOperationsDbContext : DbContext
{
    public AdminOperationsDbContext(DbContextOptions<AdminOperationsDbContext> options)
        : base(options)
    {
    }

    public DbSet<AdminAuditLog> AdminAuditLogs { get; set; } = null!;
    public DbSet<AdminDashboardSetting> AdminDashboardSettings { get; set; } = null!;

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        modelBuilder.Entity<AdminAuditLog>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.HasIndex(e => e.AuditUuid).IsUnique();
            entity.HasIndex(e => e.AdminUserUuid);
            entity.HasIndex(e => e.Action);
            entity.HasIndex(e => new { e.ResourceType, e.ResourceUuid });
            entity.HasIndex(e => e.CreatedAt);
        });

        modelBuilder.Entity<AdminDashboardSetting>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.HasIndex(e => e.SettingKey).IsUnique();
        });
    }
}
