using Microsoft.EntityFrameworkCore;
using PaymentService.Entities;

namespace PaymentService.Data;

public class PaymentDbContext : DbContext
{
    public PaymentDbContext(DbContextOptions<PaymentDbContext> options) : base(options)
    {
    }

    public DbSet<Payment> Payments => Set<Payment>();
    public DbSet<PaymentTransaction> PaymentTransactions => Set<PaymentTransaction>();
    public DbSet<PaymentRefund> PaymentRefunds => Set<PaymentRefund>();
    public DbSet<PaymentWebhookEvent> PaymentWebhookEvents => Set<PaymentWebhookEvent>();
    public DbSet<PaymentIdempotencyRecord> PaymentIdempotencyRecords => Set<PaymentIdempotencyRecord>();
    public DbSet<FinancialLedgerEntry> FinancialLedgerEntries => Set<FinancialLedgerEntry>();
    public DbSet<DriverPayoutRecord> DriverPayoutRecords => Set<DriverPayoutRecord>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        // Payments
        modelBuilder.Entity<Payment>(entity =>
        {
            entity.HasIndex(e => e.PaymentUuid).IsUnique();
            entity.HasIndex(e => e.BookingUuid);
            entity.HasIndex(e => e.RiderUuid);
            entity.HasIndex(e => e.DriverUuid);
            entity.HasIndex(e => e.Status);
            entity.HasIndex(e => e.ProviderOrderId);
            entity.HasIndex(e => e.ProviderPaymentId);
            entity.HasIndex(e => e.IdempotencyKey);
            entity.Property(e => e.PaymentMethod).HasConversion<string>();
            entity.Property(e => e.Status).HasConversion<string>();
        });

        // PaymentTransactions
        modelBuilder.Entity<PaymentTransaction>(entity =>
        {
            entity.HasIndex(e => e.TransactionUuid).IsUnique();
            entity.HasIndex(e => e.PaymentId);
            entity.HasIndex(e => e.PaymentUuid);
            entity.HasIndex(e => e.GatewayOrderId);
            entity.HasIndex(e => e.GatewayPaymentId);
            entity.Property(e => e.AttemptType).HasConversion<string>();
            entity.Property(e => e.Status).HasConversion<string>();

            entity.HasOne(t => t.Payment)
                .WithMany(p => p.Transactions)
                .HasForeignKey(t => t.PaymentId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        // PaymentRefunds
        modelBuilder.Entity<PaymentRefund>(entity =>
        {
            entity.HasIndex(e => e.RefundUuid).IsUnique();
            entity.HasIndex(e => e.PaymentId);
            entity.HasIndex(e => e.PaymentUuid);
            entity.HasIndex(e => e.ProviderRefundId);
            entity.Property(e => e.Status).HasConversion<string>();

            entity.HasOne(r => r.Payment)
                .WithMany(p => p.Refunds)
                .HasForeignKey(r => r.PaymentId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        // PaymentWebhookEvents
        modelBuilder.Entity<PaymentWebhookEvent>(entity =>
        {
            entity.HasIndex(e => e.EventUuid).IsUnique();
            entity.HasIndex(e => new { e.Provider, e.ProviderEventId }).IsUnique();
            entity.Property(e => e.Status).HasConversion<string>();
        });

        // PaymentIdempotencyRecords
        modelBuilder.Entity<PaymentIdempotencyRecord>(entity =>
        {
            entity.HasIndex(e => new { e.CallerUuid, e.IdempotencyKey }).IsUnique();
        });

        // FinancialLedgerEntries
        modelBuilder.Entity<FinancialLedgerEntry>(entity =>
        {
            entity.HasIndex(e => e.EntryUuid).IsUnique();
            entity.HasIndex(e => e.PaymentUuid);
            entity.HasIndex(e => e.BookingUuid);
            entity.HasIndex(e => e.DriverUuid);
            entity.Property(e => e.EntryType).HasConversion<string>();
            entity.Property(e => e.Direction).HasConversion<string>();
            entity.Property(e => e.Account).HasConversion<string>();
        });

        // DriverPayoutRecords
        modelBuilder.Entity<DriverPayoutRecord>(entity =>
        {
            entity.HasIndex(e => e.PayoutUuid).IsUnique();
            entity.HasIndex(e => e.DriverUuid);
            entity.Property(e => e.Status).HasConversion<string>();
        });
    }
}
