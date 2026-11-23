package com.lexor.booking.client;

import com.lexor.booking.config.BookingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Resilient HTTP client for asynchronous OTP dispatch via notification-service.
 * Failures in notification delivery are logged and do not roll back core booking creation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationClient {

    private final BookingProperties bookingProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    public boolean dispatchOtp(String recipientPhone, String recipientEmail, String otpCode, int expirySeconds) {
        String url = bookingProperties.getNotificationServiceUrl() + "/api/v1/internal/notifications/otp";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(bookingProperties.getInternalApiKey())) {
                headers.set("X-Internal-Service-Key", bookingProperties.getInternalApiKey());
            }

            Map<String, Object> body = Map.of(
                    "recipientPhone", recipientPhone != null ? recipientPhone : "",
                    "recipientEmail", recipientEmail != null ? recipientEmail : "",
                    "otpCode", otpCode,
                    "expirySeconds", expirySeconds
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            log.warn("Non-fatal: failed to dispatch OTP SMS via notification-service: {}", ex.getMessage());
            return false;
        }
    }

    public boolean dispatchBusinessEvent(String eventType, String eventId, String recipientUuid, String recipientRole, String relatedEntityType, String relatedEntityUuid, Map<String, Object> metadata) {
        String url = bookingProperties.getNotificationServiceUrl() + "/api/v1/internal/notifications/events";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(bookingProperties.getInternalApiKey())) {
                headers.set("X-Internal-Service-Key", bookingProperties.getInternalApiKey());
            }

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("eventId", eventId != null ? eventId : java.util.UUID.randomUUID().toString());
            body.put("eventType", eventType);
            body.put("sourceService", "booking-service");
            body.put("recipientUuid", recipientUuid != null ? recipientUuid : "SYSTEM_BROADCAST");
            body.put("recipientRole", recipientRole != null ? recipientRole : "RIDER");
            body.put("relatedEntityType", relatedEntityType != null ? relatedEntityType : "BOOKING");
            body.put("relatedEntityUuid", relatedEntityUuid);
            body.put("metadata", metadata != null ? metadata : Map.of());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            log.warn("Non-fatal: failed to forward business event {} to notification-service: {}", eventType, ex.getMessage());
            return false;
        }
    }
}
