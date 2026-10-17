package com.lexor.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lexor.matching.entity.MatchingStatus;
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
@Schema(description = "Driver matching attempt result and reservation details")
public class MatchingAttemptResponse {

    @Schema(description = "Matching attempt public UUID identifier", example = "99999999-9999-4999-8999-999999999999")
    private String attemptUuid;

    @Schema(description = "Referenced ride request public UUID", example = "44444444-4444-4444-8444-444444444444")
    private String rideRequestUuid;

    @Schema(description = "Current lifecycle status of the matching attempt", example = "MATCHED")
    private MatchingStatus status;

    @Schema(description = "Attempt iteration count for this ride request", example = "1")
    private Integer attemptNumber;

    @Schema(description = "Total eligible driver candidates evaluated", example = "5")
    private Integer candidatesEvaluated;

    @Schema(description = "Matched driver details (null if NO_DRIVERS_FOUND or PENDING)")
    private DriverCandidateResponse driver;

    @Schema(description = "Matched vehicle details (null if NO_DRIVERS_FOUND or PENDING)")
    private VehicleSummaryResponse vehicle;

    @Schema(description = "Distance in km to matched driver", example = "2.35")
    private BigDecimal distanceKm;

    @Schema(description = "Ranking score of the matched candidate", example = "88.50")
    private BigDecimal matchScore;

    @Schema(description = "Reason description if matching failed or no drivers found", example = "No eligible drivers online within 10.0 km radius")
    private String failureReason;

    @Schema(description = "Timestamp when the matching attempt was initiated", example = "2026-10-17T12:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp when the matching attempt finished or resolved", example = "2026-10-17T12:00:01")
    private LocalDateTime completedAt;
}
