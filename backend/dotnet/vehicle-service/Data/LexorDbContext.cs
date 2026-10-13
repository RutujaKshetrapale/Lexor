using Microsoft.EntityFrameworkCore;
using VehicleService.Entities;

namespace VehicleService.Data;

public class LexorDbContext : DbContext
{
    public LexorDbContext(DbContextOptions<LexorDbContext> options) : base(options)
    {
    }

    public DbSet<User> Users { get; set; } = null!;
    public DbSet<DriverProfile> DriverProfiles { get; set; } = null!;
    public DbSet<Vehicle> Vehicles { get; set; } = null!;
    public DbSet<VehicleDocument> VehicleDocuments { get; set; } = null!;
    public DbSet<VehicleVerificationHistory> VehicleVerificationHistories { get; set; } = null!;
    public DbSet<DriverVehicleAssignment> DriverVehicleAssignments { get; set; } = null!;

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
                .HasConversion<string>();

            entity.Property(e => e.Status)
                .HasConversion<string>();
        });

        modelBuilder.Entity<DriverProfile>(entity =>
        {
            entity.ToTable("driver_profiles");
            entity.HasKey(e => e.Id);
            entity.HasIndex(e => e.UserId).IsUnique();

            entity.Property(e => e.Status)
                .HasConversion<string>();

            entity.Property(e => e.BackgroundCheckStatus)
                .HasConversion<string>();

            entity.Property(e => e.OnboardingStatus)
                .HasConversion<string>();

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

        modelBuilder.Entity<Vehicle>(entity =>
        {
            entity.ToTable("vehicles");
            entity.HasKey(e => e.Id);
            entity.HasIndex(e => e.Uuid).IsUnique();
            entity.HasIndex(e => e.LicensePlate).IsUnique();
            entity.HasIndex(e => e.Vin).IsUnique();
            entity.HasIndex(e => e.VerificationStatus);
            entity.HasIndex(e => e.FuelType);

            entity.Property(e => e.FuelType)
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

        modelBuilder.Entity<VehicleDocument>(entity =>
        {
            entity.ToTable("vehicle_documents");
            entity.HasKey(e => e.Id);
            entity.HasIndex(e => e.VehicleId);
            entity.HasIndex(e => e.VerificationStatus);
            entity.HasIndex(e => e.DocumentType);
            entity.HasIndex(e => new { e.VehicleId, e.IsCurrent });

            entity.Property(e => e.DocumentType)
                .HasConversion<string>();

            entity.Property(e => e.VerificationStatus)
                .HasConversion<string>();

            entity.HasOne(e => e.Vehicle)
                .WithMany()
                .HasForeignKey(e => e.VehicleId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(e => e.ReviewedByAdmin)
                .WithMany()
                .HasForeignKey(e => e.ReviewedByAdminId)
                .OnDelete(DeleteBehavior.SetNull);
        });

        modelBuilder.Entity<VehicleVerificationHistory>(entity =>
        {
            entity.ToTable("vehicle_verification_history");
            entity.HasKey(e => e.Id);
            entity.HasIndex(e => e.VehicleId);
            entity.HasIndex(e => e.DocumentId);
            entity.HasIndex(e => e.ReviewedByAdminId);
            entity.HasIndex(e => e.CreatedAt);

            entity.HasOne(e => e.Vehicle)
                .WithMany()
                .HasForeignKey(e => e.VehicleId)
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

        modelBuilder.Entity<DriverVehicleAssignment>(entity =>
        {
            entity.ToTable("driver_vehicle_assignments");
            entity.HasKey(e => e.Id);
            entity.HasIndex(e => e.VehicleId);
            entity.HasIndex(e => e.DriverId);
            entity.HasIndex(e => e.Status);
            entity.HasIndex(e => new { e.VehicleId, e.Status });

            entity.Property(e => e.Status)
                .HasConversion<string>();

            entity.HasOne(e => e.Vehicle)
                .WithMany()
                .HasForeignKey(e => e.VehicleId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(e => e.Driver)
                .WithMany()
                .HasForeignKey(e => e.DriverId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(e => e.AssignedByAdmin)
                .WithMany()
                .HasForeignKey(e => e.AssignedByAdminId)
                .OnDelete(DeleteBehavior.SetNull);
        });
    }
}
