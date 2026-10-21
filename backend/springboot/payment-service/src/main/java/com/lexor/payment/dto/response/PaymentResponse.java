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
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Payment entity response")
public class PaymentResponse {

    @Schema(description = "Public payment UUID identifier", example = "99999999-9999-4999-8999-999999999999")
    private String paymentUuid;

    @Schema(description = "Associated booking public UUID identifier", example = "55555555-5555-4555-8555-555555555555")
    private String bookingUuid;

    @Schema(description = "Associated rider public UUID identifier", example = "22222222-2222-4222-8222-222222222222")
    private String riderUuid;

    @Schema(description = "Associated driver public UUID identifier", example = "33333333-3333-4333-8333-333333333333")
    private String driverUuid;

    @Schema(description = "Payment amount", example = "250.00")
    private BigDecimal amount;

    @Schema(description = "Payment amount in minor units (paise)", example = "25000")
    private Long amountPaise;

    @Schema(description = "Currency code", example = "INR")
    private String currency;

    @Schema(description = "Selected payment method (UPI, CARD, NET_BANKING, CASH)", example = "UPI")
    private PaymentMethod paymentMethod;

    @Schema(description = "Current lifecycle status of the payment", example = "SUCCEEDED")
    private PaymentStatus status;

    @Schema(description = "Payment provider name", example = "RAZORPAY")
    private String provider;

    @Schema(description = "Provider order ID", example = "order_98374928374823")
    private String providerOrderId;

    @Schema(description = "Authoritative provider payment ID", example = "pay_98374928374823")
    private String providerPaymentId;

    @Schema(description = "Client SDK checkout payload / QR / intent URI")
    private Map<String, Object> checkoutPayload;

    @Schema(description = "Failure reason if payment failed", example = "Insufficient funds")
    private String failureReason;

    @Schema(description = "Timestamp when payment was authorized")
    private LocalDateTime authorizedAt;

    @Schema(description = "Timestamp when payment was captured / settled")
    private LocalDateTime capturedAt;

    @Schema(description = "Timestamp when payment record was created")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp when payment record was last updated")
    private LocalDateTime updatedAt;
}
