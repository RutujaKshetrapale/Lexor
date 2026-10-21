package com.lexor.payment.dto.response;

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
@Schema(description = "Platform financial reconciliation overview")
public class ReconciliationResponse {

    @Schema(description = "Total number of payments recorded", example = "1240")
    private long totalPaymentsCount;

    @Schema(description = "Total revenue/fare collected", example = "310000.00")
    private BigDecimal totalCollectedAmount;

    @Schema(description = "Total cash collected by drivers", example = "120000.00")
    private BigDecimal totalCashCollected;

    @Schema(description = "Total collected via digital payment gateway", example = "190000.00")
    private BigDecimal totalDigitalCollected;

    @Schema(description = "Total platform commission earned", example = "62000.00")
    private BigDecimal totalCommissionEarned;

    @Schema(description = "Total driver net earnings allocated", example = "248000.00")
    private BigDecimal totalDriverEarnings;

    @Schema(description = "Total refund amount disbursed", example = "5000.00")
    private BigDecimal totalRefundsDisbursed;

    @Schema(description = "Number of unresolved reconciliation discrepancies", example = "0")
    private long discrepanciesCount;

    @Schema(description = "Reconciliation status (HEALTHY, ATTENTION_REQUIRED)", example = "HEALTHY")
    private String status;

    @Schema(description = "Timestamp when report was generated")
    private LocalDateTime generatedAt;
}
