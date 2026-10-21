package com.lexor.payment.dto.response;

import com.lexor.payment.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Enabled payment method details")
public class PaymentMethodDto {

    @Schema(description = "Payment method code", example = "UPI")
    private PaymentMethod method;

    @Schema(description = "Display name", example = "UPI (Google Pay, PhonePe, Paytm, QR)")
    private String displayName;

    @Schema(description = "Detailed description", example = "Instant payment via UPI App or scan-to-pay QR code")
    private String description;

    @Schema(description = "Whether method is currently enabled", example = "true")
    private boolean enabled;

    @Schema(description = "Whether method involves external payment gateway session", example = "true")
    private boolean requiresProviderCheckout;
}
