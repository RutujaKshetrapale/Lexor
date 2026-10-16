package com.lexor.riderequest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Detailed fare calculation breakdown")
public class FareEstimateResponse {

    @Schema(description = "Estimated route distance in kilometers", example = "11.50")
    private BigDecimal distanceKm;

    @Schema(description = "Estimated route duration in minutes", example = "32")
    private Integer durationMinutes;

    @Schema(description = "Base fare amount", example = "50.00")
    private BigDecimal baseFare;

    @Schema(description = "Distance-based fare amount", example = "172.50")
    private BigDecimal distanceFare;

    @Schema(description = "Time-based fare amount", example = "80.00")
    private BigDecimal timeFare;

    @Schema(description = "Subtotal computed fare before minimum threshold check", example = "302.50")
    private BigDecimal subtotalFare;

    @Schema(description = "Minimum fare enforced by category", example = "80.00")
    private BigDecimal minimumFare;

    @Schema(description = "Final estimated fare", example = "302.50")
    private BigDecimal finalEstimatedFare;

    @Schema(description = "Fare currency code", example = "INR")
    @Builder.Default
    private String currency = "INR";
}
