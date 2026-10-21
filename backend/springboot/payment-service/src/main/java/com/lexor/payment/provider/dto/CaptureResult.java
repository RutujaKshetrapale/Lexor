package com.lexor.payment.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaptureResult {
    private boolean successful;
    private String paymentId;
    private String orderId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String failureReason;
}
