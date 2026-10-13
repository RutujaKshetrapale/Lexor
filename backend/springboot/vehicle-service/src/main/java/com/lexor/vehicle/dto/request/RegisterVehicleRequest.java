package com.lexor.vehicle.dto.request;

import com.lexor.vehicle.entity.FuelType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to register a new vehicle under the authenticated driver's account")
public class RegisterVehicleRequest {

    @NotBlank(message = "Vehicle make is required")
    @Size(max = 50, message = "Make must not exceed 50 characters")
    @Schema(description = "Vehicle make / manufacturer", example = "Toyota")
    private String make;

    @NotBlank(message = "Vehicle model is required")
    @Size(max = 50, message = "Model must not exceed 50 characters")
    @Schema(description = "Vehicle model", example = "Urban Cruiser Hyryder")
    private String model;

    @NotNull(message = "Manufacturing year is required")
    @Min(value = 1990, message = "Manufacturing year must be 1990 or newer")
    @Max(value = 2035, message = "Manufacturing year cannot be in distant future")
    @Schema(description = "Manufacturing year", example = "2023")
    private Integer year;

    @NotBlank(message = "Vehicle color is required")
    @Size(max = 30, message = "Color must not exceed 30 characters")
    @Schema(description = "Exterior color", example = "Midnight Black")
    private String color;

    @NotBlank(message = "License plate / registration number is required")
    @Size(min = 4, max = 20, message = "License plate must be between 4 and 20 characters")
    @Schema(description = "Official registration license plate", example = "KA-01-EQ-4829")
    private String licensePlate;

    @Size(max = 17, message = "VIN must not exceed 17 characters")
    @Schema(description = "Vehicle Identification Number (VIN/Chassis)", example = "MA31234567890ABCD")
    private String vin;

    @NotBlank(message = "Ride type category is required")
    @Size(max = 30, message = "Category must not exceed 30 characters")
    @Schema(description = "Ride type / vehicle category (e.g. SEDAN, SUV, MINI, HATCHBACK, AUTO, GREEN)", example = "SEDAN")
    private String rideTypeCategory;

    @NotNull(message = "Seating capacity is required")
    @Min(value = 1, message = "Seat capacity must be at least 1")
    @Max(value = 60, message = "Seat capacity must not exceed 60")
    @Schema(description = "Seating capacity including passenger seats", example = "4")
    private Integer seatCapacity;

    @NotNull(message = "Fuel type is required")
    @Schema(description = "Propulsion / fuel type", example = "HYBRID")
    private FuelType fuelType;
}
