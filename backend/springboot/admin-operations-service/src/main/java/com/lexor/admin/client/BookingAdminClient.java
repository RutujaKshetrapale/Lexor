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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingAdminClient {

    private final RestTemplate restTemplate;

    @Value("${lexor.services.booking-service-url:http://localhost:8095}")
    private String bookingServiceUrl;

    @Value("${lexor.security.internal-api-key:lexor-internal-service-secret-key-2026}")
    private String internalApiKey;

    public Map<String, Object> getBookingMetrics(LocalDateTime start, LocalDateTime end) {
        Map<String, Object> result = new HashMap<>();
        result.put("totalRides", 0L);
        result.put("completedRides", 0L);
        result.put("cancelledRides", 0L);
        result.put("activeRides", 0L);
        result.put("grossBookingValue", BigDecimal.ZERO);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service-Key", internalApiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // Fetch admin bookings page 0 to sample counts and totals
            String url = bookingServiceUrl + "/api/v1/admin/bookings?size=100";
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
                result.put("totalRides", totalCount);

                Object contentObj = body.get("content");
                if (contentObj instanceof List<?> list) {
                    long completed = 0;
                    long cancelled = 0;
                    long active = 0;
                    BigDecimal gross = BigDecimal.ZERO;

                    for (Object item : list) {
                        if (item instanceof Map<?, ?> map) {
                            String status = String.valueOf(map.get("status"));
                            if ("COMPLETED".equals(status)) {
                                completed++;
                            } else if (status.startsWith("CANCELLED") || "DECLINED_BY_DRIVER".equals(status) || "EXPIRED".equals(status)) {
                                cancelled++;
                            } else if ("IN_PROGRESS".equals(status) || "ACCEPTED".equals(status) || "ARRIVED".equals(status) || "CONFIRMED".equals(status)) {
                                active++;
                            }

                            Object fareObj = map.get("fareAmount");
                            if (fareObj == null) fareObj = map.get("totalFare");
                            if (fareObj instanceof Number num) {
                                gross = gross.add(BigDecimal.valueOf(num.doubleValue()));
                            }
                        }
                    }

                    result.put("completedRides", completed);
                    result.put("cancelledRides", cancelled);
                    result.put("activeRides", active);
                    result.put("grossBookingValue", gross);
                }
            }
        } catch (Exception e) {
            log.warn("Could not query booking metrics from {}: {}", bookingServiceUrl, e.getMessage());
        }

        return result;
    }

    public List<LiveOperationsSnapshotResponse.ActiveRideItem> getActiveRides() {
        List<LiveOperationsSnapshotResponse.ActiveRideItem> activeRides = new ArrayList<>();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service-Key", internalApiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            String url = bookingServiceUrl + "/api/v1/admin/bookings?status=IN_PROGRESS&size=20";
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
                            activeRides.add(LiveOperationsSnapshotResponse.ActiveRideItem.builder()
                                    .bookingUuid((String) map.get("bookingUuid"))
                                    .rideUuid((String) map.get("rideUuid"))
                                    .riderUuid((String) map.get("riderUuid"))
                                    .driverUuid((String) map.get("driverUuid"))
                                    .pickupAddress((String) map.get("pickupAddress"))
                                    .dropoffAddress((String) map.get("dropoffAddress"))
                                    .status((String) map.get("status"))
                                    .estimatedFare(map.get("fareAmount") instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO)
                                    .startTime(LocalDateTime.now())
                                    .build());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not query active rides from booking service: {}", e.getMessage());
        }
        return activeRides;
    }

    public List<LiveOperationsSnapshotResponse.RecentCancellationItem> getRecentCancellations() {
        List<LiveOperationsSnapshotResponse.RecentCancellationItem> cancellations = new ArrayList<>();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service-Key", internalApiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            String url = bookingServiceUrl + "/api/v1/admin/bookings?size=15";
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
                            String status = String.valueOf(map.get("status"));
                            if (status.startsWith("CANCELLED")) {
                                cancellations.add(LiveOperationsSnapshotResponse.RecentCancellationItem.builder()
                                        .bookingUuid((String) map.get("bookingUuid"))
                                        .cancelledBy(status.contains("RIDER") ? "RIDER" : status.contains("DRIVER") ? "DRIVER" : "ADMIN")
                                        .reason((String) map.get("cancellationReason"))
                                        .cancelledAt(LocalDateTime.now())
                                        .build());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not query recent cancellations from booking service: {}", e.getMessage());
        }
        return cancellations;
    }
}
