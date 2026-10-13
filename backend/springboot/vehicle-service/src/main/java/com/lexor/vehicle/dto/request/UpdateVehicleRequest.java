package com.lexor.vehicle.dto.request;

import com.lexor.vehicle.entity.FuelType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to update permitted vehicle details")
public class UpdateVehicleRequest {

    @Size(max = 50, message = "Make must not exceed 50 characters")
    @Schema(description = "Vehicle make / manufacturer", example = "Toyota")
    private String make;

    @Size(max = 50, message = "Model must not exceed 50 characters")
    @Schema(description = "Vehicle model", example = "Urban Cruiser Hyryder")
    private String model;

    @Min(value = 1990, message = "Manufacturing year must be 1990 or newer")
    @Max(value = 2035, message = "Manufacturing year cannot be in distant future")
    @Schema(description = "Manufacturing year", example = "2023")
    private Integer year;

    @Size(max = 30, message = "Color must not exceed 30 characters")
    @Schema(description = "Exterior color", example = "Pearl White")
    private String color;

    @Size(max = 17, message = "VIN must not exceed 17 characters")
    @Schema(description = "Vehicle Identification Number", example = "MA31234567890ABCD")
    private String vin;

    @Size(max = 30, message = "Category must not exceed 30 characters")
    @Schema(description = "Vehicle category", example = "SEDAN")
    private String rideTypeCategory;

    @Min(value = 1, message = "Seat capacity must be at least 1")
    @Max(value = 60, message = "Seat capacity must not exceed 60")
    @Schema(description = "Seating capacity", example = "4")
    private Integer seatCapacity;

    @Schema(description = "Fuel type", example = "HYBRID")
    private FuelType fuelType;
}
