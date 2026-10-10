using Microsoft.EntityFrameworkCore;
using UserService.Entities;

namespace UserService.Data;

public class LexorDbContext : DbContext
{
    public LexorDbContext(DbContextOptions<LexorDbContext> options) : base(options)
    {
    }

    public DbSet<User> Users { get; set; } = null!;

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
    }
}
