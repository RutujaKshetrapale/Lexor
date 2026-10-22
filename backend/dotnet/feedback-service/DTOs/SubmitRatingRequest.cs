using System.ComponentModel.DataAnnotations;

namespace FeedbackService.DTOs;

public class SubmitRatingRequest
{
    [Required(ErrorMessage = "Rating is required")]
    [Range(1, 5, ErrorMessage = "Rating must be between 1 and 5")]
    public int Rating { get; set; }

    [MaxLength(1000, ErrorMessage = "Review text cannot exceed 1000 characters")]
    public string? ReviewText { get; set; }

    public List<string>? TagCodes { get; set; }
}
