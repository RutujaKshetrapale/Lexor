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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DriverAdminClient {

    private final RestTemplate restTemplate;

    @Value("${lexor.services.driver-service-url:http://localhost:8085}")
    private String driverServiceUrl;

    @Value("${lexor.security.internal-api-key:}")
    private String internalApiKey;

    public Map<String, Object> getDriverMetrics() {
        Map<String, Object> result = new HashMap<>();
        result.put("totalDrivers", 0L);
        result.put("onlineDrivers", 0L);
        result.put("inTripDrivers", 0L);
        result.put("pendingDriverVerifications", 0L);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service-Key", internalApiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            String url = driverServiceUrl + "/api/v1/admin/drivers?size=100";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Object totalEl = body.get("totalElements");
                long totalCount = totalEl instanceof Number ? ((Number) totalEl).longValue() : 0L;
                result.put("totalDrivers", totalCount);

                Object contentObj = body.get("content");
                if (contentObj instanceof List<?> list) {
                    long online = 0;
                    long inTrip = 0;
                    long pendingVerif = 0;

                    for (Object item : list) {
                        if (item instanceof Map<?, ?> map) {
                            String status = String.valueOf(map.get("status"));
                            String onboarding = String.valueOf(map.get("onboardingStatus"));

                            if ("ONLINE".equals(status)) online++;
                            if ("IN_TRIP".equals(status)) inTrip++;
                            if ("PENDING_VERIFICATION".equals(onboarding) || "UNDER_REVIEW".equals(onboarding)) {
                                pendingVerif++;
                            }
                        }
                    }

                    result.put("onlineDrivers", online);
                    result.put("inTripDrivers", inTrip);
                    result.put("pendingDriverVerifications", pendingVerif);
                }
            }
        } catch (Exception e) {
            log.warn("Could not query driver metrics from {}: {}", driverServiceUrl, e.getMessage());
        }

        return result;
    }
}
