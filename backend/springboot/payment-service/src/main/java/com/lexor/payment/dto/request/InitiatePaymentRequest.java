package com.lexor.payment.dto.request;

import com.lexor.payment.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to initiate ride payment")
public class InitiatePaymentRequest {

    @NotBlank(message = "Booking UUID is required")
    @Schema(description = "Authoritative booking public UUID identifier", example = "55555555-5555-4555-8555-555555555555")
    private String bookingUuid;

    @NotNull(message = "Payment method is required")
    @Schema(description = "Selected payment method (UPI, CARD, NET_BANKING, CASH)", example = "UPI")
    private PaymentMethod paymentMethod;

    @Schema(description = "Optional payment gateway provider override", example = "RAZORPAY")
    private String provider;
}
