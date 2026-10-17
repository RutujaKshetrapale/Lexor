package com.lexor.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response confirming driver location update")
public class LocationUpdateResponse {

    @Schema(description = "Driver public UUID identifier", example = "33333333-3333-4333-8333-333333333333")
    private String driverUuid;

    @Schema(description = "Updated latitude coordinate", example = "12.97159870")
    private BigDecimal latitude;

    @Schema(description = "Updated longitude coordinate", example = "77.59456270")
    private BigDecimal longitude;

    @Schema(description = "Heading direction in degrees", example = "145.50")
    private BigDecimal heading;

    @Schema(description = "Speed in km/h", example = "35.20")
    private BigDecimal speedKmh;

    @Schema(description = "Timestamp when the location was recorded", example = "2026-10-17T12:00:00")
    private LocalDateTime recordedAt;

    @Schema(description = "Ingestion status", example = "SUCCESS")
    private String status;
}
