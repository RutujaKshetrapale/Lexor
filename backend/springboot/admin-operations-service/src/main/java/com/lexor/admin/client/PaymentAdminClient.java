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

    @Value("${lexor.security.internal-api-key:lexor-internal-service-secret-key-2026}")
    private String internalApiKey;

    public Map<String, Object> getFinancialReconciliation() {
        Map<String, Object> result = new HashMap<>();
        result.put("successfulPaymentTotals", BigDecimal.ZERO);
        result.put("refundTotals", BigDecimal.ZERO);
        result.put("platformCommissionEarned", BigDecimal.ZERO);
        result.put("totalPaymentsCount", 0L);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service-Key", internalApiKey);
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
                result.put("successfulPaymentTotals", parseBigDecimal(body.get("totalCollectedAmount")));
                result.put("refundTotals", parseBigDecimal(body.get("totalRefundsDisbursed")));
                result.put("platformCommissionEarned", parseBigDecimal(body.get("totalCommissionEarned")));
                result.put("totalPaymentsCount", parseLong(body.get("totalPaymentsCount")));
            }
        } catch (Exception e) {
            log.warn("Could not query payment reconciliation from {}: {}", paymentServiceUrl, e.getMessage());
        }

        return result;
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
