package com.lexor.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lexor.payment.enums.PaymentMethod;
import com.lexor.payment.enums.PaymentStatus;
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
@Schema(description = "Official ride payment receipt")
public class PaymentReceiptResponse {

    @Schema(description = "Receipt reference number", example = "RCP-20261021-8849")
    private String receiptNumber;

    @Schema(description = "Payment public UUID identifier", example = "99999999-9999-4999-8999-999999999999")
    private String paymentUuid;

    @Schema(description = "Booking public UUID identifier", example = "55555555-5555-4555-8555-555555555555")
    private String bookingUuid;

    @Schema(description = "Payment timestamp", example = "2026-10-21T10:30:00")
    private LocalDateTime paidAt;

    @Schema(description = "Rider full name", example = "Rajesh Kumar")
    private String riderName;

    @Schema(description = "Driver full name", example = "Vikram Singh")
    private String driverName;

    @Schema(description = "Vehicle description", example = "Toyota Camry")
    private String vehicleModel;

    @Schema(description = "Vehicle registration plate", example = "KA01AB1234")
    private String licensePlate;

    @Schema(description = "Trip pickup address", example = "Indiranagar, Bengaluru")
    private String pickupAddress;

    @Schema(description = "Trip destination address", example = "Koramangala, Bengaluru")
    private String dropAddress;

    @Schema(description = "Payment method used", example = "UPI")
    private PaymentMethod paymentMethod;

    @Schema(description = "Lifecycle status", example = "SUCCEEDED")
    private PaymentStatus status;

    @Schema(description = "Itemized fare breakdown")
    private FareBreakdown fareBreakdown;

    @Schema(description = "Total amount paid", example = "250.00")
    private BigDecimal amountPaid;

    @Schema(description = "Currency code", example = "INR")
    private String currency;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FareBreakdown {
        private BigDecimal baseFare;
        private BigDecimal distanceFare;
        private BigDecimal timeFare;
        private BigDecimal taxAmount;
        private BigDecimal totalFare;
        private BigDecimal platformCommission;
        private BigDecimal driverEarnings;
    }
}
