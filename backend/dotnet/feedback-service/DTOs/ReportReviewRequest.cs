using System.ComponentModel.DataAnnotations;
using FeedbackService.Domain.Enums;

namespace FeedbackService.DTOs;

public class ReportReviewRequest
{
    [Required(ErrorMessage = "Report reason is required")]
    public ReportReason Reason { get; set; }

    [MaxLength(500, ErrorMessage = "Comments cannot exceed 500 characters")]
    public string? Comments { get; set; }
}
