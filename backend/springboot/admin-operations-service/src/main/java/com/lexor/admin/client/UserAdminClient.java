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
public class UserAdminClient {

    private final RestTemplate restTemplate;

    @Value("${lexor.services.user-service-url:http://localhost:8083}")
    private String userServiceUrl;

    @Value("${lexor.security.internal-api-key:}")
    private String internalApiKey;

    public Map<String, Object> getUserMetrics() {
        Map<String, Object> result = new HashMap<>();
        result.put("registeredRiders", 0L);
        result.put("registeredDrivers", 0L);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service-Key", internalApiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // Fetch riders count
            String url = userServiceUrl + "/api/v1/users?role=RIDER&size=1";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object totalEl = response.getBody().get("totalElements");
                long ridersCount = totalEl instanceof Number ? ((Number) totalEl).longValue() : 0L;
                result.put("registeredRiders", ridersCount);
            }

            // Fetch drivers count from user service
            String driverUrl = userServiceUrl + "/api/v1/users?role=DRIVER&size=1";
            ResponseEntity<Map<String, Object>> driverResponse = restTemplate.exchange(
                    driverUrl,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );

            if (driverResponse.getStatusCode().is2xxSuccessful() && driverResponse.getBody() != null) {
                Object totalEl = driverResponse.getBody().get("totalElements");
                long driversCount = totalEl instanceof Number ? ((Number) totalEl).longValue() : 0L;
                result.put("registeredDrivers", driversCount);
            }
        } catch (Exception e) {
            log.warn("Could not query user metrics from {}: {}", userServiceUrl, e.getMessage());
        }

        return result;
    }
}
