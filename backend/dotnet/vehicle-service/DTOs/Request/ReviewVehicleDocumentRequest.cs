using System.ComponentModel.DataAnnotations;
using VehicleService.Enums;

namespace VehicleService.DTOs.Request;

public class ReviewVehicleDocumentRequest
{
    [Required(ErrorMessage = "Document verification status is required")]
    public DocumentVerificationStatus? Status { get; set; }

    [StringLength(500, ErrorMessage = "Rejection reason must not exceed 500 characters")]
    public string? RejectionReason { get; set; }

    [StringLength(500, ErrorMessage = "Review notes must not exceed 500 characters")]
    public string? ReviewNotes { get; set; }
}
