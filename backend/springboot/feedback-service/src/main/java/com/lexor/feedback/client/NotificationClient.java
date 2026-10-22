package com.lexor.feedback.client;

import com.lexor.feedback.config.FeedbackProperties;
import com.lexor.feedback.dto.request.DirectNotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationClient implements INotificationClient {

    private final RestTemplate restTemplate;
    private final FeedbackProperties properties;

    @Override
    public void sendNotification(DirectNotificationRequest request) {
        if (request == null) return;

        try {
            String url = properties.getServices().getNotificationUrl() + "/api/v1/internal/notifications/dispatch";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Service-Key", properties.getSecurity().getInternalServiceKey());

            HttpEntity<DirectNotificationRequest> entity = new HttpEntity<>(request, headers);
            restTemplate.postForEntity(url, entity, String.class);
            log.info("Dispatched notification to {} for event {}", request.getRecipientUuid(), request.getNotificationType());
        } catch (Exception ex) {
            log.warn("Non-fatal: Failed to send notification via {}: {}",
                    properties.getServices().getNotificationUrl(), ex.getMessage());
        }
    }
}
