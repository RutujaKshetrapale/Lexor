using FeedbackService.Domain.Entities;
using Microsoft.EntityFrameworkCore;

namespace FeedbackService.Data;

public class FeedbackDbContext : DbContext
{
    public FeedbackDbContext(DbContextOptions<FeedbackDbContext> options) : base(options) { }

    public DbSet<RideFeedback> Feedbacks => Set<RideFeedback>();
    public DbSet<FeedbackTag> FeedbackTags => Set<FeedbackTag>();
    public DbSet<FeedbackTagAssignment> TagAssignments => Set<FeedbackTagAssignment>();
    public DbSet<FeedbackReport> FeedbackReports => Set<FeedbackReport>();
    public DbSet<FeedbackModerationHistory> ModerationHistories => Set<FeedbackModerationHistory>();
    public DbSet<DriverRatingSummary> DriverRatingSummaries => Set<DriverRatingSummary>();
    public DbSet<RiderRatingSummary> RiderRatingSummaries => Set<RiderRatingSummary>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        // RideFeedback
        modelBuilder.Entity<RideFeedback>(entity =>
        {
            entity.HasIndex(e => new { e.RideUuid, e.ReviewerUuid, e.ReviewerRole })
                .IsUnique()
                .HasDatabaseName("uk_ride_reviewer_direction");

            entity.HasIndex(e => e.FeedbackUuid).IsUnique();
            entity.HasIndex(e => e.ReviewerUuid);
            entity.HasIndex(e => new { e.TargetUuid, e.ModerationStatus });

            entity.Property(e => e.ReviewerRole).HasConversion<string>();
            entity.Property(e => e.TargetRole).HasConversion<string>();
            entity.Property(e => e.ModerationStatus).HasConversion<string>();
        });

        // FeedbackTag
        modelBuilder.Entity<FeedbackTag>(entity =>
        {
            entity.HasIndex(e => e.TagCode).IsUnique();
            entity.Property(e => e.TargetRole).HasConversion<string>();
            entity.Property(e => e.Category).HasConversion<string>();
        });

        // FeedbackTagAssignment
        modelBuilder.Entity<FeedbackTagAssignment>(entity =>
        {
            entity.HasIndex(e => new { e.FeedbackId, e.TagId })
                .IsUnique()
                .HasDatabaseName("uk_feedback_tag");

            entity.HasOne(e => e.Feedback)
                .WithMany(f => f.TagAssignments)
                .HasForeignKey(e => e.FeedbackId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(e => e.Tag)
                .WithMany(t => t.TagAssignments)
                .HasForeignKey(e => e.TagId)
                .OnDelete(DeleteBehavior.Restrict);
        });

        // FeedbackReport
        modelBuilder.Entity<FeedbackReport>(entity =>
        {
            entity.HasIndex(e => new { e.FeedbackId, e.ReporterUuid })
                .IsUnique()
                .HasDatabaseName("uk_report_feedback_reporter");

            entity.HasIndex(e => e.ReportUuid).IsUnique();
            entity.Property(e => e.Reason).HasConversion<string>();
            entity.Property(e => e.Status).HasConversion<string>();

            entity.HasOne(e => e.Feedback)
                .WithMany(f => f.Reports)
                .HasForeignKey(e => e.FeedbackId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        // FeedbackModerationHistory
        modelBuilder.Entity<FeedbackModerationHistory>(entity =>
        {
            entity.Property(e => e.PreviousStatus).HasConversion<string>();
            entity.Property(e => e.NewStatus).HasConversion<string>();

            entity.HasOne(e => e.Feedback)
                .WithMany(f => f.ModerationHistories)
                .HasForeignKey(e => e.FeedbackId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        // DriverRatingSummary
        modelBuilder.Entity<DriverRatingSummary>(entity =>
        {
            entity.HasIndex(e => e.DriverUuid).IsUnique();
        });

        // RiderRatingSummary
        modelBuilder.Entity<RiderRatingSummary>(entity =>
        {
            entity.HasIndex(e => e.RiderUuid).IsUnique();
        });
    }
}
