package com.lexor.riderequest.client.dto;

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
public class RideTypeDto {
    private String uuid;
    private String name;
    private String code;
    private String description;
    private BigDecimal baseFare;
    private BigDecimal perKmRate;
    private BigDecimal perMinuteRate;
    private BigDecimal minimumFare;
    private BigDecimal cancellationFee;
    private Integer capacity;
    private String iconUrl;
    private Boolean isActive;
}
