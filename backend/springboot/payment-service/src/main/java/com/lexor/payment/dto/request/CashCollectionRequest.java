package com.lexor.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Driver cash collection confirmation request")
public class CashCollectionRequest {

    @Schema(description = "Optional cash amount collected by driver", example = "250.00")
    private BigDecimal amountCollected;

    @Schema(description = "Optional notes or remarks regarding collection", example = "Exact fare received in cash")
    private String notes;
}
