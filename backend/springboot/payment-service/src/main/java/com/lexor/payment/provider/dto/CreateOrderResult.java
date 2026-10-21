package com.lexor.payment.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderResult {
    private String orderId;
    private Long amountPaise;
    private BigDecimal amount;
    private String currency;
    private String providerKeyId;
    private Map<String, Object> checkoutPayload;
}
