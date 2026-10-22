using System.Security.Claims;
using FeedbackService.Configuration;
using FeedbackService.Data;
using FeedbackService.Domain.Entities;
using FeedbackService.Domain.Enums;
using FeedbackService.DTOs;
using FeedbackService.Exceptions;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace FeedbackService.Services;

public class ModerationService : IModerationService
{
    private readonly FeedbackDbContext _dbContext;
    private readonly IRatingCalculationService _ratingCalculationService;
    private readonly FeedbackSettings _settings;
    private readonly ILogger<ModerationService> _logger;

    public ModerationService(
        FeedbackDbContext dbContext,
        IRatingCalculationService ratingCalculationService,
        IOptions<FeedbackSettings> settings,
        ILogger<ModerationService> logger)
    {
        _dbContext = dbContext;
        _ratingCalculationService = ratingCalculationService;
        _settings = settings.Value;
        _logger = logger;
    }

    private static string GetUserId(ClaimsPrincipal user)
    {
        return user.FindFirstValue(ClaimTypes.NameIdentifier)
            ?? user.FindFirstValue("sub")
            ?? user.FindFirstValue("uuid")
            ?? string.Empty;
    }

    public async Task<FeedbackReportResponse> ReportReviewAsync(ClaimsPrincipal reporter, string reviewUuid, ReportReviewRequest request)
    {
        var reporterUuid = GetUserId(reporter);
        var feedback = await _dbContext.Feedbacks
            .FirstOrDefaultAsync(f => f.FeedbackUuid == reviewUuid);

        if (feedback == null)
            throw new ResourceNotFoundException($"Review not found with UUID: {reviewUuid}");

        var exists = await _dbContext.FeedbackReports
            .AnyAsync(r => r.FeedbackId == feedback.Id && r.ReporterUuid == reporterUuid);

        if (exists)
            throw new ConflictException("You have already reported this review.");

        var report = new FeedbackReport
        {
            ReportUuid = Guid.NewGuid().ToString(),
            FeedbackId = feedback.Id,
            Feedback = feedback,
            ReporterUuid = reporterUuid,
            Reason = request.Reason,
            Comments = request.Comments,
            Status = ReportStatus.PENDING,
            CreatedAt = DateTime.UtcNow
        };

        _dbContext.FeedbackReports.Add(report);
        await _dbContext.SaveChangesAsync();

        // Check if report threshold reached for automatic UNDER_REVIEW
        var reportCount = await _dbContext.FeedbackReports
            .CountAsync(r => r.FeedbackId == feedback.Id);

        if (reportCount >= _settings.ReportAutoReviewThreshold && feedback.ModerationStatus == ModerationStatus.VISIBLE)
        {
            _logger.LogInformation("Auto-moderating review {Review} to UNDER_REVIEW due to {Count} reports",
                reviewUuid, reportCount);

            feedback.ModerationStatus = ModerationStatus.UNDER_REVIEW;
            feedback.UpdatedAt = DateTime.UtcNow;

            _dbContext.ModerationHistories.Add(new FeedbackModerationHistory
            {
                FeedbackId = feedback.Id,
                Feedback = feedback,
                ModeratorUuid = "SYSTEM_AUTO_MODERATION",
                PreviousStatus = ModerationStatus.VISIBLE,
                NewStatus = ModerationStatus.UNDER_REVIEW,
                Reason = $"Automated review flag triggered: exceeded report threshold ({reportCount})",
                ModeratedAt = DateTime.UtcNow
            });

            await _dbContext.SaveChangesAsync();

            // Recalculate target summary because review is no longer VISIBLE
            await _ratingCalculationService.RecalculateSummaryForTargetAsync(feedback.TargetUuid, feedback.TargetRole);
        }

        return FeedbackReportResponse.FromEntity(report);
    }

    public async Task<PageResponse<FeedbackReportResponse>> GetReportsAsync(ReportStatus? status, int page, int size)
    {
        var query = _dbContext.FeedbackReports
            .Include(r => r.Feedback)
            .AsQueryable();

        if (status.HasValue)
        {
            query = query.Where(r => r.Status == status.Value);
        }

        var total = await query.CountAsync();
        var items = await query
            .OrderByDescending(r => r.CreatedAt)
            .Skip(page * size)
            .Take(size)
            .ToListAsync();

        var dtos = items.Select(FeedbackReportResponse.FromEntity).ToList();
        return PageResponse<FeedbackReportResponse>.Create(dtos, page, size, total);
    }

    public async Task<FeedbackResponse> ApplyModerationActionAsync(ClaimsPrincipal moderator, string reviewUuid, ModerationActionRequest request)
    {
        var moderatorUuid = GetUserId(moderator);
        var feedback = await _dbContext.Feedbacks
            .Include(f => f.TagAssignments)
            .ThenInclude(a => a.Tag)
            .FirstOrDefaultAsync(f => f.FeedbackUuid == reviewUuid);

        if (feedback == null)
            throw new ResourceNotFoundException($"Review not found with UUID: {reviewUuid}");

        var oldStatus = feedback.ModerationStatus;
        var newStatus = request.NewStatus;

        if (oldStatus == newStatus)
            throw new BadRequestException($"Review is already in status: {newStatus}");

        if (!oldStatus.CanTransitionTo(newStatus))
            throw new BadRequestException($"Invalid moderation transition from {oldStatus} to {newStatus}");

        feedback.ModerationStatus = newStatus;
        feedback.UpdatedAt = DateTime.UtcNow;

        _dbContext.ModerationHistories.Add(new FeedbackModerationHistory
        {
            FeedbackId = feedback.Id,
            Feedback = feedback,
            ModeratorUuid = moderatorUuid,
            PreviousStatus = oldStatus,
            NewStatus = newStatus,
            Reason = request.Reason,
            ModeratedAt = DateTime.UtcNow
        });

        await _dbContext.SaveChangesAsync();
        _logger.LogInformation("Moderator {Mod} transitioned review {Rev} from {Old} to {New}. Reason: {Reason}",
            moderatorUuid, reviewUuid, oldStatus, newStatus, request.Reason);

        if (oldStatus == ModerationStatus.VISIBLE || newStatus == ModerationStatus.VISIBLE)
        {
            await _ratingCalculationService.RecalculateSummaryForTargetAsync(feedback.TargetUuid, feedback.TargetRole);
        }

        return FeedbackResponse.FromEntity(feedback);
    }

    public async Task<List<ModerationHistoryResponse>> GetModerationHistoryAsync(string reviewUuid)
    {
        var feedback = await _dbContext.Feedbacks
            .FirstOrDefaultAsync(f => f.FeedbackUuid == reviewUuid);

        if (feedback == null)
            throw new ResourceNotFoundException($"Review not found with UUID: {reviewUuid}");

        var list = await _dbContext.ModerationHistories
            .Where(h => h.FeedbackId == feedback.Id)
            .OrderByDescending(h => h.ModeratedAt)
            .ToListAsync();

        return list.Select(ModerationHistoryResponse.FromEntity).ToList();
    }

    public async Task<FeedbackAnalyticsResponse> GetAnalyticsAsync()
    {
        var total = await _dbContext.Feedbacks.LongCountAsync();
        var visible = await _dbContext.Feedbacks.LongCountAsync(f => f.ModerationStatus == ModerationStatus.VISIBLE);
        var underReview = await _dbContext.Feedbacks.LongCountAsync(f => f.ModerationStatus == ModerationStatus.UNDER_REVIEW);
        var hidden = await _dbContext.Feedbacks.LongCountAsync(f => f.ModerationStatus == ModerationStatus.HIDDEN);
        var removed = await _dbContext.Feedbacks.LongCountAsync(f => f.ModerationStatus == ModerationStatus.REMOVED);

        var visibleRatings = await _dbContext.Feedbacks
            .Where(f => f.ModerationStatus == ModerationStatus.VISIBLE)
            .Select(f => f.Rating)
            .ToListAsync();

        decimal avg = visibleRatings.Count > 0
            ? Math.Round((decimal)visibleRatings.Sum() / visibleRatings.Count, 2, MidpointRounding.AwayFromZero)
            : 5.00m;

        var dist = new Dictionary<int, long>
        {
            [5] = visibleRatings.Count(r => r == 5),
            [4] = visibleRatings.Count(r => r == 4),
            [3] = visibleRatings.Count(r => r == 3),
            [2] = visibleRatings.Count(r => r == 2),
            [1] = visibleRatings.Count(r => r == 1)
        };

        return new FeedbackAnalyticsResponse
        {
            TotalFeedbackCount = total,
            VisibleFeedbackCount = visible,
            UnderReviewFeedbackCount = underReview,
            HiddenFeedbackCount = hidden,
            RemovedFeedbackCount = removed,
            PlatformAverageRating = avg,
            RatingsDistribution = dist,
            TopTags = new List<TagCountDto>()
        };
    }
}
