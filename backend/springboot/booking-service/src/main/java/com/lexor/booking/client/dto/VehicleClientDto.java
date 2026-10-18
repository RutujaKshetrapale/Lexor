package com.lexor.booking.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class VehicleClientDto {

    private String vehicleUuid;
    private String make;
    private String model;
    private Integer year;
    private String color;
    private String licensePlate;
    private String rideTypeCategory;
}
