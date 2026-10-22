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
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class VehicleAdminClient {

    private final RestTemplate restTemplate;

    @Value("${lexor.services.vehicle-service-url:http://localhost:8087}")
    private String vehicleServiceUrl;

    @Value("${lexor.security.internal-api-key:}")
    private String internalApiKey;

    public Map<String, Object> getVehicleMetrics() {
        Map<String, Object> result = new HashMap<>();
        result.put("registeredVehicles", 0L);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service-Key", internalApiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            String url = vehicleServiceUrl + "/api/v1/admin/vehicles?size=1";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object totalEl = response.getBody().get("totalElements");
                long totalCount = totalEl instanceof Number ? ((Number) totalEl).longValue() : 0L;
                result.put("registeredVehicles", totalCount);
            }
        } catch (Exception e) {
            log.warn("Could not query vehicle metrics from {}: {}", vehicleServiceUrl, e.getMessage());
        }

        return result;
    }
}
