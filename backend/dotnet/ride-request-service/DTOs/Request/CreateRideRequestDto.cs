using System.ComponentModel.DataAnnotations;

namespace RideRequestService.DTOs.Request;

public class CreateRideRequestDto
{
    [Required(ErrorMessage = "Pickup address is required")]
    [StringLength(255, MinimumLength = 3, ErrorMessage = "Pickup address must be between 3 and 255 characters")]
    public string PickupAddress { get; set; } = string.Empty;

    [Required(ErrorMessage = "Pickup latitude is required")]
    [Range(-90.0, 90.0, ErrorMessage = "Pickup latitude must be between -90.0 and 90.0")]
    public decimal? PickupLatitude { get; set; }

    [Required(ErrorMessage = "Pickup longitude is required")]
    [Range(-180.0, 180.0, ErrorMessage = "Pickup longitude must be between -180.0 and 180.0")]
    public decimal? PickupLongitude { get; set; }

    [Required(ErrorMessage = "Drop address is required")]
    [StringLength(255, MinimumLength = 3, ErrorMessage = "Drop address must be between 3 and 255 characters")]
    public string DropAddress { get; set; } = string.Empty;

    [Required(ErrorMessage = "Drop latitude is required")]
    [Range(-90.0, 90.0, ErrorMessage = "Drop latitude must be between -90.0 and 90.0")]
    public decimal? DropLatitude { get; set; }

    [Required(ErrorMessage = "Drop longitude is required")]
    [Range(-180.0, 180.0, ErrorMessage = "Drop longitude must be between -180.0 and 180.0")]
    public decimal? DropLongitude { get; set; }

    public string? RideTypeUuid { get; set; }

    public string? RideTypeCode { get; set; }

    public long? CouponId { get; set; }
}
