using Microsoft.EntityFrameworkCore;
using NotificationService.Domain.Entities;

namespace NotificationService.Data;

public class NotificationDbContext : DbContext
{
    public NotificationDbContext(DbContextOptions<NotificationDbContext> options) : base(options)
    {
    }

    public DbSet<Notification> Notifications => Set<Notification>();
    public DbSet<NotificationDelivery> NotificationDeliveries => Set<NotificationDelivery>();
    public DbSet<NotificationDevice> NotificationDevices => Set<NotificationDevice>();
    public DbSet<NotificationPreference> NotificationPreferences => Set<NotificationPreference>();
    public DbSet<NotificationEventInbox> NotificationEventInbox => Set<NotificationEventInbox>();
    public DbSet<NotificationTemplate> NotificationTemplates => Set<NotificationTemplate>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        // Notifications
        modelBuilder.Entity<Notification>(entity =>
        {
            entity.HasIndex(e => e.NotificationUuid).IsUnique();
            entity.HasIndex(e => new { e.RecipientUuid, e.IsRead, e.CreatedAt });
            entity.HasIndex(e => e.Category);
            entity.HasIndex(e => e.NotificationType);
            entity.HasIndex(e => e.Status);
            entity.HasIndex(e => e.SourceEventId);

            entity.Property(e => e.RecipientRole).HasConversion<string>();
            entity.Property(e => e.Category).HasConversion<string>();
            entity.Property(e => e.Status).HasConversion<string>();
        });

        // Deliveries
        modelBuilder.Entity<NotificationDelivery>(entity =>
        {
            entity.HasIndex(e => e.DeliveryUuid).IsUnique();
            entity.HasIndex(e => e.NotificationUuid);
            entity.HasIndex(e => e.Channel);
            entity.HasIndex(e => e.Status);
            entity.HasIndex(e => e.ProviderMessageId);

            entity.Property(e => e.Channel).HasConversion<string>();
            entity.Property(e => e.Status).HasConversion<string>();
        });

        // Devices
        modelBuilder.Entity<NotificationDevice>(entity =>
        {
            entity.HasIndex(e => e.DeviceUuid).IsUnique();
            entity.HasIndex(e => new { e.UserUuid, e.IsActive });
            entity.HasIndex(e => e.FcmToken);

            entity.Property(e => e.Platform).HasConversion<string>();
        });

        // Preferences
        modelBuilder.Entity<NotificationPreference>(entity =>
        {
            entity.HasIndex(e => e.PreferenceUuid).IsUnique();
            entity.HasIndex(e => e.UserUuid).IsUnique();
        });

        // Event Inbox
        modelBuilder.Entity<NotificationEventInbox>(entity =>
        {
            entity.HasIndex(e => e.EventUuid).IsUnique();
            entity.HasIndex(e => e.EventId).IsUnique();
            entity.HasIndex(e => e.EventType);
            entity.HasIndex(e => e.Status);

            entity.Property(e => e.Status).HasConversion<string>();
        });

        // Templates
        modelBuilder.Entity<NotificationTemplate>(entity =>
        {
            entity.HasIndex(e => new { e.TemplateCode, e.Channel, e.Locale }).IsUnique();
            entity.Property(e => e.Channel).HasConversion<string>();
        });
    }
}
