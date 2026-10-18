using Microsoft.EntityFrameworkCore;
using BookingService.Entities;

namespace BookingService.Data;

public class LexorDbContext : DbContext
{
    public LexorDbContext(DbContextOptions<LexorDbContext> options) : base(options)
    {
    }

    public DbSet<User> Users => Set<User>();
    public DbSet<RiderProfile> RiderProfiles => Set<RiderProfile>();
    public DbSet<DriverProfile> DriverProfiles => Set<DriverProfile>();
    public DbSet<Vehicle> Vehicles => Set<Vehicle>();
    public DbSet<RideType> RideTypes => Set<RideType>();
    public DbSet<RideRequest> RideRequests => Set<RideRequest>();
    public DbSet<MatchingAttempt> MatchingAttempts => Set<MatchingAttempt>();
    public DbSet<DriverReservation> DriverReservations => Set<DriverReservation>();
    public DbSet<Booking> Bookings => Set<Booking>();
    public DbSet<Ride> Rides => Set<Ride>();

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

        modelBuilder.Entity<RideRequest>()
            .Property(rr => rr.Status)
            .HasConversion<string>();

        modelBuilder.Entity<MatchingAttempt>()
            .Property(ma => ma.Status)
            .HasConversion<string>();

        modelBuilder.Entity<DriverReservation>()
            .Property(dr => dr.Status)
            .HasConversion<string>();

        modelBuilder.Entity<Booking>()
            .Property(b => b.BookingStatus)
            .HasConversion<string>();

        modelBuilder.Entity<Booking>()
            .HasIndex(b => b.Uuid)
            .IsUnique();

        modelBuilder.Entity<Booking>()
            .HasIndex(b => b.RideRequestId);

        modelBuilder.Entity<Booking>()
            .HasIndex(b => b.IdempotencyKey);

        modelBuilder.Entity<Ride>()
            .Property(r => r.Status)
            .HasConversion<string>();

        modelBuilder.Entity<Ride>()
            .HasIndex(r => r.Uuid)
            .IsUnique();
    }
}
