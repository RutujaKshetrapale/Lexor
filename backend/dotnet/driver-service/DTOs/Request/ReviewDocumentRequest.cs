using System.ComponentModel.DataAnnotations;
using DriverService.Enums;

namespace DriverService.DTOs.Request;

public class ReviewDocumentRequest
{
    [Required(ErrorMessage = "Document verification status is required")]
    public DocumentVerificationStatus Status { get; set; }

    [MaxLength(500, ErrorMessage = "Rejection reason cannot exceed 500 characters")]
    public string? RejectionReason { get; set; }

    [MaxLength(500, ErrorMessage = "Review notes cannot exceed 500 characters")]
    public string? ReviewNotes { get; set; }
}
