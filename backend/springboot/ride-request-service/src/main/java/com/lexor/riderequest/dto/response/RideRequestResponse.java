package com.lexor.riderequest.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lexor.riderequest.entity.RideRequestStatus;
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
@Schema(description = "Ride request details and fare estimation response")
public class RideRequestResponse {

    @Schema(description = "Public UUID identifier of the ride request", example = "44444444-4444-4444-8444-444444444444")
    private String requestUuid;

    @Schema(description = "Public UUID identifier of the authenticated rider", example = "22222222-2222-4222-8222-222222222222")
    private String riderUuid;

    @Schema(description = "Full name of the rider", example = "Rajesh Kumar")
    private String riderName;

    @Schema(description = "Email of the rider", example = "rajesh.kumar@example.com")
    private String riderEmail;

    @Schema(description = "Pickup street address or landmark", example = "MG Road, Indiranagar, Bengaluru")
    private String pickupAddress;

    @Schema(description = "Pickup latitude coordinate", example = "12.97840000")
    private BigDecimal pickupLatitude;

    @Schema(description = "Pickup longitude coordinate", example = "77.64080000")
    private BigDecimal pickupLongitude;

    @Schema(description = "Destination drop-off street address or landmark", example = "Prestige Tech Park, Marathahalli, Bengaluru")
    private String dropAddress;

    @Schema(description = "Destination drop-off latitude coordinate", example = "12.93720000")
    private BigDecimal dropLatitude;

    @Schema(description = "Destination drop-off longitude coordinate", example = "77.69490000")
    private BigDecimal dropLongitude;

    @Schema(description = "Ride category unique code", example = "GO")
    private String rideTypeCode;

    @Schema(description = "Ride category display name", example = "LEXOR Go")
    private String rideTypeName;

    @Schema(description = "Estimated journey distance in kilometers", example = "11.50")
    private BigDecimal estimatedDistanceKm;

    @Schema(description = "Estimated travel duration in minutes", example = "32")
    private Integer estimatedDurationMinutes;

    @Schema(description = "Estimated total trip fare", example = "250.00")
    private BigDecimal estimatedFare;

    @Schema(description = "Monetary currency code", example = "INR")
    @Builder.Default
    private String currency = "INR";

    @Schema(description = "Current lifecycle status of the ride request", example = "REQUESTED")
    private RideRequestStatus status;

    @Schema(description = "Timestamp when the request was submitted", example = "2026-10-16T12:00:00")
    private LocalDateTime requestedAt;

    @Schema(description = "Record creation timestamp", example = "2026-10-16T12:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Record last updated timestamp", example = "2026-10-16T12:00:00")
    private LocalDateTime updatedAt;
}
