package com.lexor.notification.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexor.notification.dto.request.BusinessEventRequest;
import com.lexor.notification.dto.request.DirectNotificationRequest;
import com.lexor.notification.dto.response.NotificationDto;
import com.lexor.notification.entity.NotificationEventInboxEntity;
import com.lexor.notification.entity.NotificationPreferenceEntity;
import com.lexor.notification.enums.*;
import com.lexor.notification.exception.DuplicateEventException;
import com.lexor.notification.repository.NotificationEventInboxRepository;
import com.lexor.notification.service.IEventIngestionService;
import com.lexor.notification.service.INotificationService;
import com.lexor.notification.service.IPreferenceService;
import com.lexor.notification.service.ITemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventIngestionServiceImpl implements IEventIngestionService {

    private final NotificationEventInboxRepository eventInboxRepository;
    private final INotificationService notificationService;
    private final IPreferenceService preferenceService;
    private final ITemplateService templateService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public NotificationDto ingestEvent(BusinessEventRequest request) {
        // 1. Idempotent Deduplication Check
        if (eventInboxRepository.existsByEventId(request.getEventId())) {
            log.warn("Duplicate business event received with eventId: {}", request.getEventId());
            throw new DuplicateEventException("Duplicate event: " + request.getEventId() + " has already been processed");
        }

        // 2. Persist event into notification_event_inbox
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            payloadJson = "{}";
        }

        NotificationEventInboxEntity inbox = NotificationEventInboxEntity.builder()
                .eventUuid(UUID.randomUUID().toString())
                .eventId(request.getEventId())
                .eventType(request.getEventType())
                .sourceService(request.getSourceService())
                .payload(payloadJson)
                .status(EventInboxStatus.PROCESSING)
                .build();

        NotificationEventInboxEntity savedInbox = eventInboxRepository.save(inbox);

        try {
            // 3. Resolve Recipient and Template
            String recipientUuid = request.getRecipientUuid();
            if (recipientUuid == null && request.getMetadata() != null) {
                recipientUuid = (String) request.getMetadata().get("riderUuid");
                if (recipientUuid == null) {
                    recipientUuid = (String) request.getMetadata().get("driverUuid");
                }
            }

            if (recipientUuid == null) {
                recipientUuid = "SYSTEM_BROADCAST";
            }

            RecipientRole role = RecipientRole.RIDER;
            if (request.getRecipientRole() != null) {
                try {
                    role = RecipientRole.valueOf(request.getRecipientRole().toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }

            NotificationPreferenceEntity pref = preferenceService.getOrCreateEntity(recipientUuid);
            String locale = pref.getPreferredLocale();

            Map<String, Object> vars = request.getMetadata() != null ? request.getMetadata() : new HashMap<>();

            // 4. Determine template code & category
            String templateCode = mapEventToTemplateCode(request.getEventType());
            NotificationCategory category = mapEventToCategory(request.getEventType());

            // 5. Render templates
            ITemplateService.RenderedTemplate inAppTpl = templateService.render(templateCode, NotificationChannel.IN_APP, locale, vars);
            ITemplateService.RenderedTemplate pushTpl = templateService.render(templateCode, NotificationChannel.PUSH, locale, vars);

            DirectNotificationRequest dispatchReq = DirectNotificationRequest.builder()
                    .recipientUuid(recipientUuid)
                    .recipientRole(role)
                    .notificationType(templateCode)
                    .category(category)
                    .title(inAppTpl.title())
                    .body(inAppTpl.body())
                    .deepLink("/rides/" + request.getRelatedEntityUuid())
                    .relatedEntityType(request.getRelatedEntityType())
                    .relatedEntityUuid(request.getRelatedEntityUuid())
                    .idempotencyKey("EVT-" + request.getEventId())
                    .channels(List.of(NotificationChannel.IN_APP, NotificationChannel.PUSH))
                    .build();

            NotificationDto result = notificationService.dispatchNotification(dispatchReq);

            // 6. Update inbox status to PROCESSED
            savedInbox.setStatus(EventInboxStatus.PROCESSED);
            savedInbox.setProcessedAt(LocalDateTime.now());
            eventInboxRepository.save(savedInbox);

            log.info("Successfully processed business event {} (type={})", request.getEventId(), request.getEventType());
            return result;

        } catch (Exception ex) {
            log.error("Failed to process event {}: {}", request.getEventId(), ex.getMessage());
            savedInbox.setStatus(EventInboxStatus.FAILED);
            savedInbox.setErrorMessage(ex.getMessage());
            savedInbox.setRetryCount(savedInbox.getRetryCount() + 1);
            eventInboxRepository.save(savedInbox);
            throw ex;
        }
    }

    private String mapEventToTemplateCode(String eventType) {
        if (eventType == null) return "GENERAL_ALERT";
        return switch (eventType) {
            case "RideRequestCreated" -> "RIDE_REQUEST_CREATED";
            case "DriverMatched" -> "DRIVER_MATCHED";
            case "DriverAssigned" -> "DRIVER_ASSIGNED";
            case "DriverArrived" -> "DRIVER_ARRIVED";
            case "RideStarted" -> "RIDE_STARTED";
            case "RideCompleted" -> "RIDE_COMPLETED";
            case "RideCancelled" -> "RIDE_CANCELLED";
            case "PaymentSucceeded" -> "PAYMENT_SUCCEEDED";
            case "PaymentFailed" -> "PAYMENT_FAILED";
            case "RefundInitiated" -> "REFUND_INITIATED";
            case "RefundCompleted" -> "REFUND_COMPLETED";
            case "DriverOfferCreated" -> "DRIVER_OFFER";
            case "UserSecurityEvent" -> "SECURITY_ALERT";
            default -> eventType.toUpperCase();
        };
    }

    private NotificationCategory mapEventToCategory(String eventType) {
        if (eventType == null) return NotificationCategory.SYSTEM;
        if (eventType.startsWith("Payment") || eventType.startsWith("Refund")) {
            return NotificationCategory.PAYMENT;
        }
        if (eventType.contains("Security")) {
            return NotificationCategory.SECURITY;
        }
        if (eventType.startsWith("Ride") || eventType.startsWith("Driver")) {
            return NotificationCategory.RIDE;
        }
        return NotificationCategory.SYSTEM;
    }
}
