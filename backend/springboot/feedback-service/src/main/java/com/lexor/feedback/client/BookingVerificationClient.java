package com.lexor.feedback.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexor.feedback.config.FeedbackProperties;
import com.lexor.feedback.dto.client.RideEligibilityDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingVerificationClient implements IBookingVerificationClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final FeedbackProperties properties;

    private final Map<String, RideEligibilityDto> mockRideRegistry = new ConcurrentHashMap<>();

    @Override
    public Optional<RideEligibilityDto> verifyRideEligibility(String rideOrBookingUuid, String authorizationHeader) {
        if (rideOrBookingUuid == null || rideOrBookingUuid.isBlank()) {
            return Optional.empty();
        }

        // 1. Check in-memory test registry first
        if (mockRideRegistry.containsKey(rideOrBookingUuid)) {
            return Optional.of(mockRideRegistry.get(rideOrBookingUuid));
        }

        // 2. Perform RPC call to Booking Service
        try {
            String url = properties.getServices().getBookingUrl() + "/api/v1/admin/bookings/" + rideOrBookingUuid;
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service-Key", properties.getSecurity().getInternalServiceKey());
            if (authorizationHeader != null && !authorizationHeader.isBlank()) {
                headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());

                String bookingUuid = root.path("bookingUuid").asText(rideOrBookingUuid);
                String status = root.path("status").asText("CONFIRMED");
                String riderUuid = root.path("rider").path("riderUuid").asText(null);
                String driverUuid = root.path("driver").path("driverUuid").asText(null);
                String riderName = root.path("rider").path("firstName").asText("") + " " + root.path("rider").path("lastName").asText("");
                String driverName = root.path("driver").path("firstName").asText("") + " " + root.path("driver").path("lastName").asText("");

                LocalDateTime completedAt = LocalDateTime.now();
                if (root.hasNonNull("completedAt")) {
                    try {
                        completedAt = LocalDateTime.parse(root.path("completedAt").asText());
                    } catch (Exception ignored) {
                    }
                }

                RideEligibilityDto dto = RideEligibilityDto.builder()
                        .rideUuid(bookingUuid)
                        .bookingUuid(bookingUuid)
                        .status(status)
                        .riderUuid(riderUuid)
                        .driverUuid(driverUuid)
                        .riderName(riderName.trim())
                        .driverName(driverName.trim())
                        .completedAt(completedAt)
                        .build();

                return Optional.of(dto);
            }
        } catch (Exception ex) {
            log.warn("Could not verify ride eligibility from Booking Service at {}: {}",
                    properties.getServices().getBookingUrl(), ex.getMessage());
        }

        return Optional.empty();
    }

    @Override
    public void registerMockRide(RideEligibilityDto mockRide) {
        if (mockRide != null) {
            if (mockRide.getRideUuid() != null) {
                mockRideRegistry.put(mockRide.getRideUuid(), mockRide);
            }
            if (mockRide.getBookingUuid() != null) {
                mockRideRegistry.put(mockRide.getBookingUuid(), mockRide);
            }
        }
    }

    @Override
    public void clearMockRides() {
        mockRideRegistry.clear();
    }
}
