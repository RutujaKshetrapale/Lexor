package com.lexor.ridetype.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Ride type category details and fare configuration")
public class RideTypeResponse {

    @Schema(description = "Public UUID identifier", example = "a1b2c3d4-0001-4000-8000-000000000001")
    private String uuid;

    @Schema(description = "Display name of the ride type", example = "LEXOR Go")
    private String name;

    @Schema(description = "Unique category code", example = "GO")
    private String code;

    @Schema(description = "Detailed description of the ride category", example = "Affordable everyday hatchback rides for up to 4 passengers")
    private String description;

    @Schema(description = "Base fare amount in INR", example = "50.00")
    private BigDecimal baseFare;

    @Schema(description = "Per-kilometer rate in INR", example = "15.00")
    private BigDecimal perKmRate;

    @Schema(description = "Per-minute rate in INR", example = "2.50")
    private BigDecimal perMinuteRate;

    @Schema(description = "Minimum fare amount in INR", example = "80.00")
    private BigDecimal minimumFare;

    @Schema(description = "Cancellation fee in INR", example = "30.00")
    private BigDecimal cancellationFee;

    @Schema(description = "Maximum passenger seating capacity", example = "4")
    private Integer capacity;

    @Schema(description = "CDN URL for category icon", example = "https://cdn.lexor.mobility/icons/go.png")
    private String iconUrl;

    @Schema(description = "Active status for rider catalog availability", example = "true")
    private Boolean isActive;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
}
