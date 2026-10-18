package com.lexor.booking.dto.response;

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
@Schema(description = "Vehicle details summary")
public class VehicleSummaryResponse {

    @Schema(description = "Vehicle public UUID identifier", example = "e7b1a2c3-4d5e-6f7a-8b9c-0d1e2f3a4b5c")
    private String vehicleUuid;

    @Schema(description = "Make / Brand", example = "Toyota")
    private String make;

    @Schema(description = "Model name", example = "Urban Cruiser Hyryder")
    private String model;

    @Schema(description = "Manufacturing year", example = "2023")
    private Integer year;

    @Schema(description = "Exterior color", example = "Midnight Black")
    private String color;

    @Schema(description = "Official registration license plate", example = "KA-01-EQ-4829")
    private String licensePlate;
}
