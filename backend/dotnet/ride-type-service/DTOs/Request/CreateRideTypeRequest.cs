using System.ComponentModel.DataAnnotations;

namespace RideTypeService.DTOs.Request;

public class CreateRideTypeRequest
{
    [Required(ErrorMessage = "Ride type name is required")]
    [StringLength(50, MinimumLength = 2, ErrorMessage = "Ride type name must be between 2 and 50 characters")]
    public string Name { get; set; } = string.Empty;

    [Required(ErrorMessage = "Ride type code is required")]
    [StringLength(30, MinimumLength = 2, ErrorMessage = "Ride type code must be between 2 and 30 characters")]
    [RegularExpression(@"^[A-Za-z0-9_]+$", ErrorMessage = "Ride type code must contain only alphanumeric characters and underscores")]
    public string Code { get; set; } = string.Empty;

    [StringLength(1000, ErrorMessage = "Description must not exceed 1000 characters")]
    public string? Description { get; set; }

    [Required(ErrorMessage = "Base fare is required")]
    [Range(0.0, double.MaxValue, ErrorMessage = "Base fare cannot be negative")]
    public decimal BaseFare { get; set; }

    [Required(ErrorMessage = "Per km rate is required")]
    [Range(0.0, double.MaxValue, ErrorMessage = "Per km rate cannot be negative")]
    public decimal PerKmRate { get; set; }

    [Required(ErrorMessage = "Per minute rate is required")]
    [Range(0.0, double.MaxValue, ErrorMessage = "Per minute rate cannot be negative")]
    public decimal PerMinuteRate { get; set; }

    [Required(ErrorMessage = "Minimum fare is required")]
    [Range(0.0, double.MaxValue, ErrorMessage = "Minimum fare cannot be negative")]
    public decimal MinimumFare { get; set; }

    [Range(0.0, double.MaxValue, ErrorMessage = "Cancellation fee cannot be negative")]
    public decimal? CancellationFee { get; set; }

    [Required(ErrorMessage = "Passenger capacity is required")]
    [Range(1, 20, ErrorMessage = "Capacity must be between 1 and 20")]
    public int Capacity { get; set; } = 4;

    [StringLength(500, ErrorMessage = "Icon URL must not exceed 500 characters")]
    public string? IconUrl { get; set; }

    public bool? IsActive { get; set; }
}
