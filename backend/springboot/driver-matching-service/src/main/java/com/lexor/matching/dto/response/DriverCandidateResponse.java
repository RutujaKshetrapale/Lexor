package com.lexor.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Authorized driver candidate summary for matching")
public class DriverCandidateResponse {

    @Schema(description = "Driver public UUID identifier", example = "33333333-3333-4333-8333-333333333333")
    private String driverUuid;

    @Schema(description = "Driver first name", example = "Vikram")
    private String firstName;

    @Schema(description = "Driver last name", example = "Singh")
    private String lastName;

    @Schema(description = "Driver overall rating", example = "4.90")
    private BigDecimal rating;

    @Schema(description = "Total completed trips", example = "145")
    private Integer totalTrips;

    @Schema(description = "Driver acceptance rate percentage", example = "98.50")
    private BigDecimal acceptanceRate;

    @Schema(description = "Distance in kilometers between driver and ride request pickup", example = "2.35")
    private BigDecimal distanceKm;

    @Schema(description = "Calculated candidate ranking score", example = "88.50")
    private BigDecimal matchScore;
}
