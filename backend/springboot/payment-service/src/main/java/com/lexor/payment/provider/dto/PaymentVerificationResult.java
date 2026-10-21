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
public class PaymentVerificationResult {
    private boolean verified;
    private String paymentId;
    private String orderId;
    private BigDecimal amount;
    private String status;
    private String method;
    private String cardLast4;
    private String cardNetwork;
    private String vpa;
    private String bank;
}
