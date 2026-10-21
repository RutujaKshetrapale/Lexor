package com.lexor.payment.dto.request;

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
@Schema(description = "Request to retry a failed or expired payment attempt")
public class RetryPaymentRequest {

    @Schema(description = "Optional replacement payment method (e.g. switch from UPI to CASH)", example = "CASH")
    private PaymentMethod paymentMethod;
}
