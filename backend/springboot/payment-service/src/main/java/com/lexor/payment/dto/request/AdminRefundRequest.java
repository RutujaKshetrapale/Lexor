package com.lexor.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Admin-initiated refund request")
public class AdminRefundRequest {

    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "1.00", message = "Refund amount must be at least 1.00")
    @Schema(description = "Refund amount to disburse", example = "250.00")
    private BigDecimal amount;

    @NotBlank(message = "Refund reason is required")
    @Schema(description = "Customer-facing refund reason", example = "Customer dispute resolved in favor of rider")
    private String reason;

    @NotBlank(message = "Admin audit notes are required")
    @Schema(description = "Internal administrator audit notes", example = "Approved by Operations Lead under ticket #LEX-8841")
    private String adminNotes;
}
