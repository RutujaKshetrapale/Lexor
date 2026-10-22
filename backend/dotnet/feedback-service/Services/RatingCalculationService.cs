using FeedbackService.Data;
using FeedbackService.Domain.Entities;
using FeedbackService.Domain.Enums;
using FeedbackService.DTOs;
using Microsoft.EntityFrameworkCore;

namespace FeedbackService.Services;

public class RatingCalculationService : IRatingCalculationService
{
    private readonly FeedbackDbContext _dbContext;
    private readonly ILogger<RatingCalculationService> _logger;

    public RatingCalculationService(FeedbackDbContext dbContext, ILogger<RatingCalculationService> logger)
    {
        _dbContext = dbContext;
        _logger = logger;
    }

    public async Task UpdateRatingSummaryOnNewFeedbackAsync(RideFeedback feedback)
    {
        if (feedback == null) return;
        await RecalculateSummaryForTargetAsync(feedback.TargetUuid, feedback.TargetRole);
    }

    public async Task UpdateRatingSummaryOnRatingEditAsync(RideFeedback feedback, int oldRating)
    {
        if (feedback == null) return;
        await RecalculateSummaryForTargetAsync(feedback.TargetUuid, feedback.TargetRole);
    }

    public async Task RecalculateSummaryForTargetAsync(string targetUuid, FeedbackRole targetRole)
    {
        if (string.IsNullOrWhiteSpace(targetUuid)) return;

        var visibleReviews = await _dbContext.Feedbacks
            .Where(f => f.TargetUuid == targetUuid && f.ModerationStatus == ModerationStatus.VISIBLE)
            .ToListAsync();

        int totalCount = visibleReviews.Count;
        int star1 = 0, star2 = 0, star3 = 0, star4 = 0, star5 = 0;
        int sum = 0;

        foreach (var r in visibleReviews)
        {
            sum += r.Rating;
            switch (r.Rating)
            {
                case 1: star1++; break;
                case 2: star2++; break;
                case 3: star3++; break;
                case 4: star4++; break;
                case 5: star5++; break;
            }
        }

        decimal averageRating = totalCount > 0
            ? Math.Round((decimal)sum / totalCount, 2, MidpointRounding.AwayFromZero)
            : 5.00m;

        int excludedCount = await _dbContext.Feedbacks
            .CountAsync(f => f.TargetUuid == targetUuid && f.ModerationStatus != ModerationStatus.VISIBLE);

        if (targetRole == FeedbackRole.DRIVER)
        {
            var summary = await _dbContext.DriverRatingSummaries
                .FirstOrDefaultAsync(s => s.DriverUuid == targetUuid);

            if (summary == null)
            {
                summary = new DriverRatingSummary
                {
                    DriverUuid = targetUuid,
                    TotalCompletedRides = totalCount
                };
                _dbContext.DriverRatingSummaries.Add(summary);
            }

            summary.TotalRatings = totalCount;
            summary.AverageRating = averageRating;
            summary.OneStarCount = star1;
            summary.TwoStarCount = star2;
            summary.ThreeStarCount = star3;
            summary.FourStarCount = star4;
            summary.FiveStarCount = star5;
            summary.ExcludedRatingsCount = excludedCount;
            if (summary.TotalCompletedRides < totalCount)
            {
                summary.TotalCompletedRides = totalCount;
            }
            summary.LastRecalculatedAt = DateTime.UtcNow;

            await _dbContext.SaveChangesAsync();
            _logger.LogInformation("Recalculated driver summary for {Driver}: avg={Avg}, total={Total}",
                targetUuid, averageRating, totalCount);
        }
        else if (targetRole == FeedbackRole.RIDER)
        {
            var summary = await _dbContext.RiderRatingSummaries
                .FirstOrDefaultAsync(s => s.RiderUuid == targetUuid);

            if (summary == null)
            {
                summary = new RiderRatingSummary
                {
                    RiderUuid = targetUuid,
                    TotalCompletedRides = totalCount
                };
                _dbContext.RiderRatingSummaries.Add(summary);
            }

            summary.TotalRatings = totalCount;
            summary.AverageRating = averageRating;
            summary.OneStarCount = star1;
            summary.TwoStarCount = star2;
            summary.ThreeStarCount = star3;
            summary.FourStarCount = star4;
            summary.FiveStarCount = star5;
            summary.ExcludedRatingsCount = excludedCount;
            if (summary.TotalCompletedRides < totalCount)
            {
                summary.TotalCompletedRides = totalCount;
            }
            summary.LastRecalculatedAt = DateTime.UtcNow;

            await _dbContext.SaveChangesAsync();
            _logger.LogInformation("Recalculated rider summary for {Rider}: avg={Avg}, total={Total}",
                targetUuid, averageRating, totalCount);
        }
    }

    public async Task<DriverRatingSummaryResponse> GetDriverSummaryAsync(string driverUuid)
    {
        var summary = await _dbContext.DriverRatingSummaries
            .FirstOrDefaultAsync(s => s.DriverUuid == driverUuid);

        if (summary == null)
        {
            await RecalculateSummaryForTargetAsync(driverUuid, FeedbackRole.DRIVER);
            summary = await _dbContext.DriverRatingSummaries
                .FirstOrDefaultAsync(s => s.DriverUuid == driverUuid);
        }

        if (summary != null)
        {
            return DriverRatingSummaryResponse.FromEntity(summary);
        }

        return new DriverRatingSummaryResponse
        {
            DriverUuid = driverUuid,
            AverageRating = 5.00m,
            TotalRatings = 0,
            RatingDistribution = new Dictionary<int, int> { [5] = 0, [4] = 0, [3] = 0, [2] = 0, [1] = 0 },
            ExcludedRatingsCount = 0,
            TotalCompletedRides = 0,
            RecentTrend = "NEW",
            LastRecalculatedAt = DateTime.UtcNow
        };
    }

    public async Task<RiderRatingSummaryResponse> GetRiderSummaryAsync(string riderUuid)
    {
        var summary = await _dbContext.RiderRatingSummaries
            .FirstOrDefaultAsync(s => s.RiderUuid == riderUuid);

        if (summary == null)
        {
            await RecalculateSummaryForTargetAsync(riderUuid, FeedbackRole.RIDER);
            summary = await _dbContext.RiderRatingSummaries
                .FirstOrDefaultAsync(s => s.RiderUuid == riderUuid);
        }

        if (summary != null)
        {
            return RiderRatingSummaryResponse.FromEntity(summary);
        }

        return new RiderRatingSummaryResponse
        {
            RiderUuid = riderUuid,
            AverageRating = 5.00m,
            TotalRatings = 0,
            RatingDistribution = new Dictionary<int, int> { [5] = 0, [4] = 0, [3] = 0, [2] = 0, [1] = 0 },
            ExcludedRatingsCount = 0,
            TotalCompletedRides = 0,
            LastRecalculatedAt = DateTime.UtcNow
        };
    }
}
