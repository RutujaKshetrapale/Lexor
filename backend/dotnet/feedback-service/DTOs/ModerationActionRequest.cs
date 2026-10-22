using System.ComponentModel.DataAnnotations;
using FeedbackService.Domain.Enums;

namespace FeedbackService.DTOs;

public class ModerationActionRequest
{
    [Required(ErrorMessage = "New moderation status is required")]
    public ModerationStatus NewStatus { get; set; }

    [Required(ErrorMessage = "Moderation reason is mandatory for audit compliance")]
    [MaxLength(500, ErrorMessage = "Reason cannot exceed 500 characters")]
    public string Reason { get; set; } = string.Empty;
}
