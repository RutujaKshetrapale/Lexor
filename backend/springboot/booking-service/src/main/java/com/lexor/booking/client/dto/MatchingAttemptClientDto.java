package com.lexor.booking.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchingAttemptClientDto {

    private String attemptUuid;
    private String rideRequestUuid;
    private String status;
    private Integer attemptNumber;
    private Integer candidatesEvaluated;
    private DriverCandidateClientDto driver;
    private VehicleClientDto vehicle;
    private BigDecimal distanceKm;
    private BigDecimal matchScore;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
