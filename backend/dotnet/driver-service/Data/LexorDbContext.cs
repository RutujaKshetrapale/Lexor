using DriverService.Entities;
using Microsoft.EntityFrameworkCore;

namespace DriverService.Data;

public class LexorDbContext : DbContext
{
    public LexorDbContext(DbContextOptions<LexorDbContext> options) : base(options)
    {
    }

    public DbSet<User> Users { get; set; } = null!;
    public DbSet<DriverProfile> DriverProfiles { get; set; } = null!;
    public DbSet<DriverDocument> DriverDocuments { get; set; } = null!;
    public DbSet<DriverVerificationHistory> DriverVerificationHistories { get; set; } = null!;
    public DbSet<EmergencyContact> EmergencyContacts { get; set; } = null!;

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        modelBuilder.Entity<User>(entity =>
        {
            entity.ToTable("users");
            entity.HasKey(e => e.Id);
            entity.HasIndex(e => e.Email).IsUnique();
            entity.HasIndex(e => e.PhoneNumber).IsUnique();
            entity.HasIndex(e => e.Uuid).IsUnique();

            entity.Property(e => e.Role)
                .HasConversion<string>()
                .HasColumnType("enum('RIDER','DRIVER','ADMIN')");

            entity.Property(e => e.Status)
                .HasConversion<string>()
                .HasColumnType("enum('PENDING','ACTIVE','SUSPENDED','DEACTIVATED')");
        });

        modelBuilder.Entity<EmergencyContact>(entity =>
        {
            entity.ToTable("emergency_contacts");
            entity.HasKey(e => e.Id);

            entity.HasOne(e => e.User)
                .WithMany()
                .HasForeignKey(e => e.UserId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        modelBuilder.Entity<DriverProfile>(entity =>
        {
            entity.ToTable("driver_profiles");
            entity.HasKey(e => e.Id);
            entity.HasIndex(e => e.UserId).IsUnique();

            entity.Property(e => e.Status)
                .HasConversion<string>()
                .HasColumnType("enum('OFFLINE','ONLINE','IN_TRIP','SUSPENDED')");

            entity.Property(e => e.BackgroundCheckStatus)
                .HasConversion<string>()
                .HasColumnType("enum('PENDING','PASSED','FAILED')");

            entity.Property(e => e.OnboardingStatus)
                .HasConversion<string>()
                .HasColumnType("enum('INCOMPLETE','PENDING_REVIEW','UNDER_REVIEW','VERIFIED','REJECTED','SUSPENDED')");

            entity.Property(e => e.Rating)
                .HasPrecision(3, 2);

            entity.Property(e => e.AcceptanceRate)
                .HasPrecision(5, 2);

            entity.Property(e => e.CancellationRate)
                .HasPrecision(5, 2);

            entity.HasOne(e => e.User)
                .WithOne()
                .HasForeignKey<DriverProfile>(e => e.UserId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        modelBuilder.Entity<DriverDocument>(entity =>
        {
            entity.ToTable("driver_documents");
            entity.HasKey(e => e.Id);

            entity.Property(e => e.DocumentType)
                .HasConversion<string>();

            entity.Property(e => e.VerificationStatus)
                .HasConversion<string>();

            entity.HasOne(e => e.Driver)
                .WithMany()
                .HasForeignKey(e => e.DriverId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(e => e.ReviewedByAdmin)
                .WithMany()
                .HasForeignKey(e => e.ReviewedByAdminId)
                .OnDelete(DeleteBehavior.SetNull);
        });

        modelBuilder.Entity<DriverVerificationHistory>(entity =>
        {
            entity.ToTable("driver_verification_history");
            entity.HasKey(e => e.Id);

            entity.HasOne(e => e.Driver)
                .WithMany()
                .HasForeignKey(e => e.DriverId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(e => e.Document)
                .WithMany()
                .HasForeignKey(e => e.DocumentId)
                .OnDelete(DeleteBehavior.SetNull);

            entity.HasOne(e => e.ReviewedByAdmin)
                .WithMany()
                .HasForeignKey(e => e.ReviewedByAdminId)
                .OnDelete(DeleteBehavior.Cascade);
        });
    }
}
