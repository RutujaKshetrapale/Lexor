package com.lexor.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingDetailDto {

    private String bookingUuid;
    private String status;
    private String riderUuid;
    private String riderName;
    private String driverUuid;
    private String driverName;
    private String vehicleModel;
    private String licensePlate;
    private BigDecimal totalFare;
    private String currency;
    private String pickupAddress;
    private String dropAddress;
    private BigDecimal baseFare;
    private BigDecimal distanceFare;
    private BigDecimal timeFare;
    private BigDecimal taxAmount;
}
