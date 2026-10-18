package com.lexor.booking.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DriverCandidateClientDto {

    private String driverUuid;
    private String firstName;
    private String lastName;
    private BigDecimal rating;
    private Integer totalTrips;
    private BigDecimal acceptanceRate;
    private BigDecimal distanceKm;
    private BigDecimal matchScore;
}
