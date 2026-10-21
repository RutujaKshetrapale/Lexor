package com.lexor.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Rider refund request")
public class RefundPaymentRequest {

    @Schema(description = "Optional partial refund amount. If omitted, full eligible amount is refunded", example = "100.00")
    private BigDecimal amount;

    @NotBlank(message = "Refund reason is required")
    @Schema(description = "Reason for requesting refund", example = "Driver cancelled trip after pickup")
    private String reason;
}
