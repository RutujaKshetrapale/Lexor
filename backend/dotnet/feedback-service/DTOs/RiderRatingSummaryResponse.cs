using FeedbackService.Domain.Entities;

namespace FeedbackService.DTOs;

public class RiderRatingSummaryResponse
{
    public string RiderUuid { get; set; } = string.Empty;
    public decimal AverageRating { get; set; } = 5.00m;
    public int TotalRatings { get; set; }
    public Dictionary<int, int> RatingDistribution { get; set; } = new();
    public int ExcludedRatingsCount { get; set; }
    public int TotalCompletedRides { get; set; }
    public DateTime LastRecalculatedAt { get; set; }

    public static RiderRatingSummaryResponse FromEntity(RiderRatingSummary s)
    {
        var dist = new Dictionary<int, int>
        {
            [5] = s.FiveStarCount,
            [4] = s.FourStarCount,
            [3] = s.ThreeStarCount,
            [2] = s.TwoStarCount,
            [1] = s.OneStarCount
        };

        return new RiderRatingSummaryResponse
        {
            RiderUuid = s.RiderUuid,
            AverageRating = s.AverageRating,
            TotalRatings = s.TotalRatings,
            RatingDistribution = dist,
            ExcludedRatingsCount = s.ExcludedRatingsCount,
            TotalCompletedRides = s.TotalCompletedRides,
            LastRecalculatedAt = s.LastRecalculatedAt
        };
    }
}
