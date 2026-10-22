package com.lexor.admin.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentAdminClient {

    private final RestTemplate restTemplate;

    @Value("${lexor.services.payment-service-url:http://localhost:8097}")
    private String paymentServiceUrl;

    @Value("${lexor.security.internal-api-key:}")
    private String internalApiKey;

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PaymentReconciliationResult {
        private BigDecimal successfulPaymentTotals;
        private BigDecimal refundTotals;
        private BigDecimal platformCommissionEarned;
        private long totalPaymentsCount;
        private boolean isAvailable;
        private String errorMessage;
    }

    public PaymentReconciliationResult getFinancialReconciliation() {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (internalApiKey != null && !internalApiKey.isBlank()) {
                headers.set("X-Internal-Service-Key", internalApiKey);
            }
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            String url = paymentServiceUrl + "/api/v1/admin/payments/reconciliation";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                return PaymentReconciliationResult.builder()
                        .successfulPaymentTotals(parseBigDecimal(body.get("totalCollectedAmount")))
                        .refundTotals(parseBigDecimal(body.get("totalRefundsDisbursed")))
                        .platformCommissionEarned(parseBigDecimal(body.get("totalCommissionEarned")))
                        .totalPaymentsCount(parseLong(body.get("totalPaymentsCount")))
                        .isAvailable(true)
                        .build();
            } else {
                return PaymentReconciliationResult.builder()
                        .successfulPaymentTotals(BigDecimal.ZERO)
                        .refundTotals(BigDecimal.ZERO)
                        .platformCommissionEarned(BigDecimal.ZERO)
                        .totalPaymentsCount(0L)
                        .isAvailable(false)
                        .errorMessage("Unsuccessful response from payment service: " + response.getStatusCode())
                        .build();
            }
        } catch (Exception e) {
            log.warn("Could not query payment reconciliation from {}: {}", paymentServiceUrl, e.getMessage());
            return PaymentReconciliationResult.builder()
                    .successfulPaymentTotals(BigDecimal.ZERO)
                    .refundTotals(BigDecimal.ZERO)
                    .platformCommissionEarned(BigDecimal.ZERO)
                    .totalPaymentsCount(0L)
                    .isAvailable(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private BigDecimal parseBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(val.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private long parseLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(val.toString());
        } catch (Exception e) {
            return 0L;
        }
    }
}
