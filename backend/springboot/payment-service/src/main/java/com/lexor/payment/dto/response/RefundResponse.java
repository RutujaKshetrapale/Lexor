package com.lexor.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lexor.payment.enums.RefundStatus;
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
@Schema(description = "Payment refund response")
public class RefundResponse {

    @Schema(description = "Refund public UUID identifier", example = "77777777-7777-4777-8777-777777777777")
    private String refundUuid;

    @Schema(description = "Associated payment UUID identifier", example = "99999999-9999-4999-8999-999999999999")
    private String paymentUuid;

    @Schema(description = "Refund amount", example = "250.00")
    private BigDecimal amount;

    @Schema(description = "Currency code", example = "INR")
    private String currency;

    @Schema(description = "Refund status", example = "PROCESSED")
    private RefundStatus status;

    @Schema(description = "Reason for refund", example = "Driver cancelled trip")
    private String reason;

    @Schema(description = "Provider refund identifier", example = "rfnd_93849283749823")
    private String providerRefundId;

    @Schema(description = "Initiated by (RIDER, ADMIN)")
    private String initiatedBy;

    @Schema(description = "Timestamp when refund was processed")
    private LocalDateTime processedAt;

    @Schema(description = "Timestamp when refund was requested")
    private LocalDateTime createdAt;
}
