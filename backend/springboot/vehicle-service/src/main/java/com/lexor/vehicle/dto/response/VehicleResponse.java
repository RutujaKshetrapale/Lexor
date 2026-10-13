package com.lexor.vehicle.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lexor.vehicle.entity.FuelType;
import com.lexor.vehicle.entity.VehicleVerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VehicleResponse {
    private String uuid;
    private String driverUuid;
    private String driverName;
    private String make;
    private String model;
    private Integer year;
    private String color;
    private String licensePlate;
    private String vin;
    private String rideTypeCategory;
    private Integer seatCapacity;
    private FuelType fuelType;
    private boolean isActive;
    private boolean isInspected;
    private LocalDate inspectionExpiresAt;
    private VehicleVerificationStatus verificationStatus;
    private String rejectionReason;
    private LocalDateTime verifiedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
