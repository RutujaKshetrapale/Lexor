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
public class FeedbackAdminClient {

    private final RestTemplate restTemplate;

    @Value("${lexor.services.feedback-service-url:http://localhost:8101}")
    private String feedbackServiceUrl;

    @Value("${lexor.security.internal-api-key:lexor-internal-service-secret-key-2026}")
    private String internalApiKey;

    public Map<String, Object> getFeedbackMetrics() {
        Map<String, Object> result = new HashMap<>();
        result.put("averageDriverRating", BigDecimal.valueOf(5.00));
        result.put("feedbackReportsAwaitingModeration", 0L);
        Map<String, Long> starDist = new HashMap<>();
        starDist.put("1", 0L);
        starDist.put("2", 0L);
        starDist.put("3", 0L);
        starDist.put("4", 0L);
        starDist.put("5", 0L);
        result.put("starDistribution", starDist);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service-Key", internalApiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // Fetch analytics
            String analyticsUrl = feedbackServiceUrl + "/api/v1/admin/feedback/analytics";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    analyticsUrl,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Object avg = body.get("averageRating");
                if (avg instanceof Number n) {
                    result.put("averageDriverRating", BigDecimal.valueOf(n.doubleValue()));
                }

                Object distObj = body.get("ratingDistribution");
                if (distObj instanceof Map<?, ?> map) {
                    map.forEach((k, v) -> {
                        if (v instanceof Number n) {
                            starDist.put(String.valueOf(k), n.longValue());
                        }
                    });
                }
            }

            // Fetch pending reports
            String reportsUrl = feedbackServiceUrl + "/api/v1/admin/feedback/reports?status=PENDING&size=1";
            ResponseEntity<Map<String, Object>> reportsResp = restTemplate.exchange(
                    reportsUrl,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );

            if (reportsResp.getStatusCode().is2xxSuccessful() && reportsResp.getBody() != null) {
                Object totalEl = reportsResp.getBody().get("totalElements");
                if (totalEl instanceof Number n) {
                    result.put("feedbackReportsAwaitingModeration", n.longValue());
                }
            }
        } catch (Exception e) {
            log.warn("Could not query feedback metrics from {}: {}", feedbackServiceUrl, e.getMessage());
        }

        return result;
    }
}
