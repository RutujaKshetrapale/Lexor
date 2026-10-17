package com.lexor.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Summary of vehicle assigned to matched driver")
public class VehicleSummaryResponse {

    @Schema(description = "Vehicle public UUID identifier", example = "e7b1a2c3-4d5e-6f7a-8b9c-0d1e2f3a4b5c")
    private String vehicleUuid;

    @Schema(description = "Vehicle manufacturer/make", example = "Toyota")
    private String make;

    @Schema(description = "Vehicle model", example = "Urban Cruiser Hyryder")
    private String model;

    @Schema(description = "Vehicle exterior color", example = "Midnight Black")
    private String color;

    @Schema(description = "Vehicle license plate", example = "KA-01-EQ-4829")
    private String licensePlate;

    @Schema(description = "Vehicle passenger seating capacity", example = "4")
    private Integer seatCapacity;

    @Schema(description = "Vehicle fuel/propulsion type", example = "HYBRID")
    private String fuelType;

    @Schema(description = "Supported ride category", example = "GO")
    private String rideTypeCategory;
}
