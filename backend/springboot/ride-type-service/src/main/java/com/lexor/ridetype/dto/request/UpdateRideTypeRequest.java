package com.lexor.ridetype.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload for updating an existing ride type category")
public class UpdateRideTypeRequest {

    @NotBlank(message = "Ride type name is required")
    @Size(min = 2, max = 50, message = "Ride type name must be between 2 and 50 characters")
    @Schema(description = "Display name of the ride type", example = "LEXOR Comfort Plus")
    private String name;

    @Size(min = 2, max = 30, message = "Ride type code must be between 2 and 30 characters")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "Ride type code must contain only alphanumeric characters and underscores")
    @Schema(description = "Category code (optional; immutable if omitted)", example = "COMFORT_PLUS")
    private String code;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Schema(description = "Description of the ride category", example = "Updated premium sedan experience")
    private String description;

    @NotNull(message = "Base fare is required")
    @DecimalMin(value = "0.0", message = "Base fare cannot be negative")
    @Schema(description = "Base fare amount in INR", example = "80.00")
    private BigDecimal baseFare;

    @NotNull(message = "Per km rate is required")
    @DecimalMin(value = "0.0", message = "Per km rate cannot be negative")
    @Schema(description = "Per-kilometer rate in INR", example = "20.00")
    private BigDecimal perKmRate;

    @NotNull(message = "Per minute rate is required")
    @DecimalMin(value = "0.0", message = "Per minute rate cannot be negative")
    @Schema(description = "Per-minute rate in INR", example = "3.50")
    private BigDecimal perMinuteRate;

    @NotNull(message = "Minimum fare is required")
    @DecimalMin(value = "0.0", message = "Minimum fare cannot be negative")
    @Schema(description = "Minimum fare amount in INR", example = "110.00")
    private BigDecimal minimumFare;

    @DecimalMin(value = "0.0", message = "Cancellation fee cannot be negative")
    @Schema(description = "Cancellation fee in INR", example = "45.00")
    private BigDecimal cancellationFee;

    @NotNull(message = "Passenger capacity is required")
    @Min(value = 1, message = "Passenger capacity must be at least 1")
    @Max(value = 20, message = "Passenger capacity cannot exceed 20")
    @Schema(description = "Maximum passenger seating capacity", example = "4")
    private Integer capacity;

    @Size(max = 500, message = "Icon URL must not exceed 500 characters")
    @Schema(description = "CDN URL for category icon", example = "https://cdn.lexor.mobility/icons/comfort_plus.png")
    private String iconUrl;
}
