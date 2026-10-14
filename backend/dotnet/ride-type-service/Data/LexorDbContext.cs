using Microsoft.EntityFrameworkCore;
using RideTypeService.Entities;

namespace RideTypeService.Data;

public class LexorDbContext : DbContext
{
    public LexorDbContext(DbContextOptions<LexorDbContext> options) : base(options)
    {
    }

    public DbSet<RideType> RideTypes { get; set; } = null!;
    public DbSet<User> Users { get; set; } = null!;

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        modelBuilder.Entity<RideType>(entity =>
        {
            entity.ToTable("ride_types");
            entity.HasKey(e => e.Id);

            entity.HasIndex(e => e.Uuid).IsUnique();
            entity.HasIndex(e => e.Name).IsUnique();
            entity.HasIndex(e => e.Code).IsUnique();
            entity.HasIndex(e => e.IsActive);

            entity.Property(e => e.BaseFare).HasPrecision(10, 2);
            entity.Property(e => e.PerKmRate).HasPrecision(10, 2);
            entity.Property(e => e.PerMinuteRate).HasPrecision(10, 2);
            entity.Property(e => e.MinimumFare).HasPrecision(10, 2);
            entity.Property(e => e.CancellationFee).HasPrecision(10, 2);
        });

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
    }
}
