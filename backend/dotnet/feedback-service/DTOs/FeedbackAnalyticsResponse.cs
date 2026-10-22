namespace FeedbackService.DTOs;

public class FeedbackAnalyticsResponse
{
    public long TotalFeedbackCount { get; set; }
    public long VisibleFeedbackCount { get; set; }
    public long UnderReviewFeedbackCount { get; set; }
    public long HiddenFeedbackCount { get; set; }
    public long RemovedFeedbackCount { get; set; }
    public decimal PlatformAverageRating { get; set; } = 5.00m;
    public Dictionary<int, long> RatingsDistribution { get; set; } = new();
    public List<TagCountDto> TopTags { get; set; } = new();
}

public class TagCountDto
{
    public string TagCode { get; set; } = string.Empty;
    public string Label { get; set; } = string.Empty;
    public long Count { get; set; }
}
