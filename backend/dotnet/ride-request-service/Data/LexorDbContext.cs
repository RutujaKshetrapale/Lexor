using Microsoft.EntityFrameworkCore;
using RideRequestService.Entities;

namespace RideRequestService.Data;

public class LexorDbContext : DbContext
{
    public LexorDbContext(DbContextOptions<LexorDbContext> options) : base(options)
    {
    }

    public DbSet<User> Users { get; set; } = null!;
    public DbSet<RiderProfile> RiderProfiles { get; set; } = null!;
    public DbSet<RideType> RideTypes { get; set; } = null!;
    public DbSet<RideRequest> RideRequests { get; set; } = null!;

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

            entity.Property(e => e.Role).HasConversion<string>();
            entity.Property(e => e.Status).HasConversion<string>();
        });

        modelBuilder.Entity<RiderProfile>(entity =>
        {
            entity.ToTable("rider_profiles");
            entity.HasKey(e => e.Id);
            entity.HasIndex(e => e.UserId).IsUnique();

            entity.Property(e => e.Rating).HasPrecision(3, 2);

            entity.HasOne(e => e.User)
                .WithOne()
                .HasForeignKey<RiderProfile>(e => e.UserId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        modelBuilder.Entity<RideType>(entity =>
        {
            entity.ToTable("ride_types");
            entity.HasKey(e => e.Id);
            entity.HasIndex(e => e.Name).IsUnique();
            entity.HasIndex(e => e.Code).IsUnique();
            entity.HasIndex(e => e.IsActive);

            entity.Property(e => e.BaseFare).HasPrecision(10, 2);
            entity.Property(e => e.PerKmRate).HasPrecision(10, 2);
            entity.Property(e => e.PerMinuteRate).HasPrecision(10, 2);
            entity.Property(e => e.MinimumFare).HasPrecision(10, 2);
            entity.Property(e => e.CancellationFee).HasPrecision(10, 2);
        });

        modelBuilder.Entity<RideRequest>(entity =>
        {
            entity.ToTable("ride_requests");
            entity.HasKey(e => e.Id);
            entity.HasIndex(e => e.Uuid).IsUnique();
            entity.HasIndex(e => e.RiderId);
            entity.HasIndex(e => e.Status);
            entity.HasIndex(e => e.RequestedAt);

            entity.Property(e => e.Status).HasConversion<string>();

            entity.Property(e => e.PickupLatitude).HasPrecision(10, 8);
            entity.Property(e => e.PickupLongitude).HasPrecision(11, 8);
            entity.Property(e => e.DropLatitude).HasPrecision(10, 8);
            entity.Property(e => e.DropLongitude).HasPrecision(11, 8);
            entity.Property(e => e.EstimatedDistanceKm).HasPrecision(8, 2);
            entity.Property(e => e.EstimatedFare).HasPrecision(10, 2);

            entity.HasOne(e => e.Rider)
                .WithMany()
                .HasForeignKey(e => e.RiderId)
                .OnDelete(DeleteBehavior.Restrict);

            entity.HasOne(e => e.RideType)
                .WithMany()
                .HasForeignKey(e => e.RideTypeId)
                .OnDelete(DeleteBehavior.Restrict);
        });
    }
}
