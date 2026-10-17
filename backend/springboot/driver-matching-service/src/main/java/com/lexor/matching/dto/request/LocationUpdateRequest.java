package com.lexor.matching.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Driver GPS location telemetry update request")
public class LocationUpdateRequest {

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    @Schema(description = "GPS latitude coordinate", example = "12.97159870")
    private BigDecimal latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    @Schema(description = "GPS longitude coordinate", example = "77.59456270")
    private BigDecimal longitude;

    @DecimalMin(value = "0.0", message = "Heading must be between 0 and 360 degrees")
    @DecimalMax(value = "360.0", message = "Heading must be between 0 and 360 degrees")
    @Schema(description = "Vehicle heading direction in degrees (0-360)", example = "145.50")
    private BigDecimal heading;

    @DecimalMin(value = "0.0", message = "Speed cannot be negative")
    @DecimalMax(value = "250.0", message = "Speed cannot exceed 250 km/h")
    @Schema(description = "Current vehicle speed in km/h", example = "35.20")
    private BigDecimal speedKmh;

    @Schema(description = "Timestamp when the telemetry was recorded on device (defaults to server UTC time if omitted)", example = "2026-10-17T12:00:00")
    private LocalDateTime timestamp;
}
