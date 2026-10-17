using System.ComponentModel.DataAnnotations;

namespace DriverMatchingService.DTOs.Request;

public class LocationUpdateRequestDto
{
    [Required(ErrorMessage = "Latitude is required")]
    [Range(-90.0, 90.0, ErrorMessage = "Latitude must be between -90 and 90")]
    public decimal Latitude { get; set; }

    [Required(ErrorMessage = "Longitude is required")]
    [Range(-180.0, 180.0, ErrorMessage = "Longitude must be between -180 and 180")]
    public decimal Longitude { get; set; }

    [Range(0.0, 360.0, ErrorMessage = "Heading must be between 0 and 360 degrees")]
    public decimal? Heading { get; set; }

    [Range(0.0, 250.0, ErrorMessage = "Speed cannot exceed 250 km/h")]
    public decimal? SpeedKmh { get; set; }

    public DateTime? Timestamp { get; set; }
}
