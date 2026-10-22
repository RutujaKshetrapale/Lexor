using FeedbackService.Domain.Entities;

namespace FeedbackService.DTOs;

public class DriverRatingSummaryResponse
{
    public string DriverUuid { get; set; } = string.Empty;
    public decimal AverageRating { get; set; } = 5.00m;
    public int TotalRatings { get; set; }
    public Dictionary<int, int> RatingDistribution { get; set; } = new();
    public int ExcludedRatingsCount { get; set; }
    public int TotalCompletedRides { get; set; }
    public string RecentTrend { get; set; } = "NEW";
    public DateTime LastRecalculatedAt { get; set; }

    public static DriverRatingSummaryResponse FromEntity(DriverRatingSummary s)
    {
        var dist = new Dictionary<int, int>
        {
            [5] = s.FiveStarCount,
            [4] = s.FourStarCount,
            [3] = s.ThreeStarCount,
            [2] = s.TwoStarCount,
            [1] = s.OneStarCount
        };

        string trend = "NEW";
        if (s.TotalRatings > 0)
        {
            if (s.AverageRating >= 4.80m) trend = "EXCELLENT";
            else if (s.AverageRating >= 4.50m) trend = "GOOD";
            else if (s.AverageRating >= 4.00m) trend = "ACCEPTABLE";
            else trend = "ATTENTION_NEEDED";
        }

        return new DriverRatingSummaryResponse
        {
            DriverUuid = s.DriverUuid,
            AverageRating = s.AverageRating,
            TotalRatings = s.TotalRatings,
            RatingDistribution = dist,
            ExcludedRatingsCount = s.ExcludedRatingsCount,
            TotalCompletedRides = s.TotalCompletedRides,
            RecentTrend = trend,
            LastRecalculatedAt = s.LastRecalculatedAt
        };
    }
}
