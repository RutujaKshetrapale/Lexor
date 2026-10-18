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
public class RideRequestClientDto {

    private String requestUuid;
    private String riderUuid;
    private String riderName;
    private String riderEmail;
    private String pickupAddress;
    private BigDecimal pickupLatitude;
    private BigDecimal pickupLongitude;
    private String dropAddress;
    private BigDecimal dropLatitude;
    private BigDecimal dropLongitude;
    private String rideTypeCode;
    private String rideTypeName;
    private BigDecimal estimatedDistanceKm;
    private Integer estimatedDurationMinutes;
    private BigDecimal estimatedFare;
    private String currency;
    private String status;
    private LocalDateTime requestedAt;
}
