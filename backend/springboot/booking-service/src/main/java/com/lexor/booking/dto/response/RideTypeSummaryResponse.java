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
@Schema(description = "Ride category configuration summary")
public class RideTypeSummaryResponse {

    @Schema(description = "Ride type public UUID", example = "a1b2c3d4-0001-4000-8000-000000000001")
    private String rideTypeUuid;

    @Schema(description = "Category code", example = "GO")
    private String code;

    @Schema(description = "Category display name", example = "LEXOR Go")
    private String name;
}
