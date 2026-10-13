using System.ComponentModel.DataAnnotations;
using VehicleService.Enums;

namespace VehicleService.DTOs.Request;

public class UpdateVehicleRequest
{
    [StringLength(50, ErrorMessage = "Make must not exceed 50 characters")]
    public string? Make { get; set; }

    [StringLength(50, ErrorMessage = "Model must not exceed 50 characters")]
    public string? Model { get; set; }

    [Range(1990, 2035, ErrorMessage = "Manufacturing year must be between 1990 and 2035")]
    public int? Year { get; set; }

    [StringLength(30, ErrorMessage = "Color must not exceed 30 characters")]
    public string? Color { get; set; }

    [StringLength(17, ErrorMessage = "VIN must not exceed 17 characters")]
    public string? Vin { get; set; }

    [StringLength(30, ErrorMessage = "Category must not exceed 30 characters")]
    public string? RideTypeCategory { get; set; }

    [Range(1, 60, ErrorMessage = "Seat capacity must be between 1 and 60")]
    public int? SeatCapacity { get; set; }

    public FuelType? FuelType { get; set; }
}
