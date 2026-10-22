package com.lexor.admin.client;

import com.lexor.admin.dto.response.LiveOperationsSnapshotResponse;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RideRequestAdminClient {

    private final RestTemplate restTemplate;

    @Value("${lexor.services.ride-request-service-url:http://localhost:8091}")
    private String rideRequestServiceUrl;

    @Value("${lexor.security.internal-api-key:lexor-internal-service-secret-key-2026}")
    private String internalApiKey;

    public Map<String, Object> getRideRequestMetrics() {
        Map<String, Object> result = new HashMap<>();
        result.put("pendingRideRequests", 0L);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service-Key", internalApiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            String url = rideRequestServiceUrl + "/api/v1/admin/ride-requests?status=PENDING&size=1";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object totalEl = response.getBody().get("totalElements");
                long count = totalEl instanceof Number ? ((Number) totalEl).longValue() : 0L;
                result.put("pendingRideRequests", count);
            }
        } catch (Exception e) {
            log.warn("Could not query ride request metrics from {}: {}", rideRequestServiceUrl, e.getMessage());
        }

        return result;
    }

    public List<LiveOperationsSnapshotResponse.PendingRequestItem> getPendingRequests() {
        List<LiveOperationsSnapshotResponse.PendingRequestItem> pendingList = new ArrayList<>();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service-Key", internalApiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            String url = rideRequestServiceUrl + "/api/v1/admin/ride-requests?status=PENDING&size=20";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object contentObj = response.getBody().get("content");
                if (contentObj instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> map) {
                            pendingList.add(LiveOperationsSnapshotResponse.PendingRequestItem.builder()
                                    .requestUuid((String) map.get("requestUuid"))
                                    .riderUuid((String) map.get("riderUuid"))
                                    .rideType((String) map.get("rideType"))
                                    .pickupAddress((String) map.get("pickupAddress"))
                                    .dropoffAddress((String) map.get("dropoffAddress"))
                                    .status((String) map.get("status"))
                                    .matchingAttemptsCount(map.get("matchingAttemptsCount") instanceof Number n ? n.intValue() : 1)
                                    .requestedAt(LocalDateTime.now().minusMinutes(2))
                                    .waitingSeconds(120)
                                    .build());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not query pending requests from ride request service: {}", e.getMessage());
        }
        return pendingList;
    }
}
