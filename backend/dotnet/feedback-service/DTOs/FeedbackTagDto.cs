using FeedbackService.Domain.Entities;
using FeedbackService.Domain.Enums;

namespace FeedbackService.DTOs;

public class FeedbackTagDto
{
    public string TagCode { get; set; } = string.Empty;
    public string Label { get; set; } = string.Empty;
    public TagTargetRole TargetRole { get; set; }
    public TagCategory Category { get; set; }

    public static FeedbackTagDto FromEntity(FeedbackTag tag)
    {
        return new FeedbackTagDto
        {
            TagCode = tag.TagCode,
            Label = tag.Label,
            TargetRole = tag.TargetRole,
            Category = tag.Category
        };
    }
}
