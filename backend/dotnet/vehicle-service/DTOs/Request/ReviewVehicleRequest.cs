using System.ComponentModel.DataAnnotations;
using VehicleService.Enums;

namespace VehicleService.DTOs.Request;

public class ReviewVehicleRequest
{
    [Required(ErrorMessage = "Decision status is required (VERIFIED or REJECTED)")]
    public VehicleVerificationStatus? Status { get; set; }

    [StringLength(500, ErrorMessage = "Rejection reason must not exceed 500 characters")]
    public string? RejectionReason { get; set; }

    [StringLength(500, ErrorMessage = "Review notes must not exceed 500 characters")]
    public string? Notes { get; set; }
}
