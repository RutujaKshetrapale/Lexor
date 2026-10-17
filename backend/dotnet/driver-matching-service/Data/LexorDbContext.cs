using Microsoft.EntityFrameworkCore;
using DriverMatchingService.Entities;

namespace DriverMatchingService.Data;

public class LexorDbContext : DbContext
{
    public LexorDbContext(DbContextOptions<LexorDbContext> options) : base(options)
    {
    }

    public DbSet<User> Users => Set<User>();
    public DbSet<DriverProfile> DriverProfiles => Set<DriverProfile>();
    public DbSet<Vehicle> Vehicles => Set<Vehicle>();
    public DbSet<DriverVehicleAssignment> DriverVehicleAssignments => Set<DriverVehicleAssignment>();
    public DbSet<RideType> RideTypes => Set<RideType>();
    public DbSet<RideRequest> RideRequests => Set<RideRequest>();
    public DbSet<DriverLocation> DriverLocations => Set<DriverLocation>();
    public DbSet<MatchingAttempt> MatchingAttempts => Set<MatchingAttempt>();
    public DbSet<DriverReservation> DriverReservations => Set<DriverReservation>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        modelBuilder.Entity<User>()
            .Property(u => u.Role)
            .HasConversion<string>();

        modelBuilder.Entity<User>()
            .Property(u => u.Status)
            .HasConversion<string>();

        modelBuilder.Entity<DriverProfile>()
            .Property(dp => dp.Status)
            .HasConversion<string>();

        modelBuilder.Entity<DriverProfile>()
            .Property(dp => dp.OnboardingStatus)
            .HasConversion<string>();

        modelBuilder.Entity<DriverProfile>()
            .Property(dp => dp.BackgroundCheckStatus)
            .HasConversion<string>();

        modelBuilder.Entity<Vehicle>()
            .Property(v => v.FuelType)
            .HasConversion<string>();

        modelBuilder.Entity<Vehicle>()
            .Property(v => v.VerificationStatus)
            .HasConversion<string>();

        modelBuilder.Entity<DriverVehicleAssignment>()
            .Property(dva => dva.Status)
            .HasConversion<string>();

        modelBuilder.Entity<RideRequest>()
            .Property(rr => rr.Status)
            .HasConversion<string>();

        modelBuilder.Entity<MatchingAttempt>()
            .Property(ma => ma.Status)
            .HasConversion<string>();

        modelBuilder.Entity<DriverReservation>()
            .Property(dr => dr.Status)
            .HasConversion<string>();
    }
}
