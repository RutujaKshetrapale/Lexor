package com.lexor.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Payload to complete the trip with actual distance and duration")
public class CompleteTripRequest {

    @DecimalMin(value = "0.0", message = "Actual distance must be non-negative")
    @Schema(description = "Actual route distance traveled in kilometers", example = "11.80")
    private BigDecimal actualDistanceKm;

    @Min(value = 0, message = "Actual duration minutes must be non-negative")
    @Schema(description = "Actual journey duration in minutes", example = "35")
    private Integer actualDurationMinutes;
}
