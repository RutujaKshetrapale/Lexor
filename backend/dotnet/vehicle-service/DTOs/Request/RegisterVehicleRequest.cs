using System.ComponentModel.DataAnnotations;
using VehicleService.Enums;

namespace VehicleService.DTOs.Request;

public class RegisterVehicleRequest
{
    [Required(ErrorMessage = "Vehicle make is required")]
    [StringLength(50, ErrorMessage = "Make must not exceed 50 characters")]
    public string Make { get; set; } = string.Empty;

    [Required(ErrorMessage = "Vehicle model is required")]
    [StringLength(50, ErrorMessage = "Model must not exceed 50 characters")]
    public string Model { get; set; } = string.Empty;

    [Required(ErrorMessage = "Manufacturing year is required")]
    [Range(1990, 2035, ErrorMessage = "Manufacturing year must be between 1990 and 2035")]
    public int? Year { get; set; }

    [Required(ErrorMessage = "Vehicle color is required")]
    [StringLength(30, ErrorMessage = "Color must not exceed 30 characters")]
    public string Color { get; set; } = string.Empty;

    [Required(ErrorMessage = "License plate / registration number is required")]
    [StringLength(20, MinimumLength = 4, ErrorMessage = "License plate must be between 4 and 20 characters")]
    public string LicensePlate { get; set; } = string.Empty;

    [StringLength(17, ErrorMessage = "VIN must not exceed 17 characters")]
    public string? Vin { get; set; }

    [Required(ErrorMessage = "Ride type category is required")]
    [StringLength(30, ErrorMessage = "Category must not exceed 30 characters")]
    public string RideTypeCategory { get; set; } = string.Empty;

    [Required(ErrorMessage = "Seating capacity is required")]
    [Range(1, 60, ErrorMessage = "Seat capacity must be between 1 and 60")]
    public int? SeatCapacity { get; set; }

    [Required(ErrorMessage = "Fuel type is required")]
    public FuelType? FuelType { get; set; }
}
