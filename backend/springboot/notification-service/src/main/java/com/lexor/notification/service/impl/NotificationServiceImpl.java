package com.lexor.notification.service.impl;

import com.lexor.notification.dto.request.DirectNotificationRequest;
import com.lexor.notification.dto.response.*;
import com.lexor.notification.entity.NotificationDeliveryEntity;
import com.lexor.notification.entity.NotificationDeviceEntity;
import com.lexor.notification.entity.NotificationEntity;
import com.lexor.notification.entity.NotificationPreferenceEntity;
import com.lexor.notification.enums.*;
import com.lexor.notification.exception.ForbiddenException;
import com.lexor.notification.exception.ResourceNotFoundException;
import com.lexor.notification.provider.IEmailNotificationProvider;
import com.lexor.notification.provider.IPushNotificationProvider;
import com.lexor.notification.provider.IRealtimeNotifier;
import com.lexor.notification.provider.ISmsNotificationProvider;
import com.lexor.notification.provider.dto.ProviderDispatchResult;
import com.lexor.notification.repository.NotificationDeliveryRepository;
import com.lexor.notification.repository.NotificationDeviceRepository;
import com.lexor.notification.repository.NotificationEventInboxRepository;
import com.lexor.notification.repository.NotificationRepository;
import com.lexor.notification.service.IDeviceService;
import com.lexor.notification.service.INotificationService;
import com.lexor.notification.service.IPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements INotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationDeviceRepository deviceRepository;
    private final NotificationEventInboxRepository eventInboxRepository;
    private final IPreferenceService preferenceService;
    private final IDeviceService deviceService;
    private final IPushNotificationProvider pushProvider;
    private final ISmsNotificationProvider smsProvider;
    private final IEmailNotificationProvider emailProvider;
    private final IRealtimeNotifier realtimeNotifier;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> getUserInbox(
            String userUuid,
            Boolean isRead,
            NotificationCategory category,
            int page,
            int size
    ) {
        int boundedSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(Math.max(page, 0), boundedSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<NotificationEntity> entityPage;
        if (category != null && isRead != null) {
            entityPage = notificationRepository.findByRecipientUuidAndCategoryAndIsReadOrderByCreatedAtDesc(userUuid, category, isRead, pageable);
        } else if (category != null) {
            entityPage = notificationRepository.findByRecipientUuidAndCategoryOrderByCreatedAtDesc(userUuid, category, pageable);
        } else if (isRead != null) {
            entityPage = notificationRepository.findByRecipientUuidAndIsReadOrderByCreatedAtDesc(userUuid, isRead, pageable);
        } else {
            entityPage = notificationRepository.findByRecipientUuidOrderByCreatedAtDesc(userUuid, pageable);
        }

        List<NotificationDto> dtos = entityPage.getContent().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        return PageResponse.<NotificationDto>builder()
                .content(dtos)
                .pageNumber(entityPage.getNumber())
                .pageSize(entityPage.getSize())
                .totalElements(entityPage.getTotalElements())
                .totalPages(entityPage.getTotalPages())
                .last(entityPage.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(String userUuid) {
        long count = notificationRepository.countByRecipientUuidAndIsReadFalse(userUuid);
        return UnreadCountResponse.builder()
                .userUuid(userUuid)
                .unreadCount(count)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationDto getNotification(String userUuid, String notificationUuid) {
        NotificationEntity notification = notificationRepository.findByNotificationUuid(notificationUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationUuid));

        if (!notification.getRecipientUuid().equals(userUuid)) {
            throw new ForbiddenException("Cannot access notification belonging to another user");
        }

        return mapToDto(notification);
    }

    @Override
    @Transactional
    public NotificationDto markAsRead(String userUuid, String notificationUuid) {
        NotificationEntity notification = notificationRepository.findByNotificationUuid(notificationUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationUuid));

        if (!notification.getRecipientUuid().equals(userUuid)) {
            throw new ForbiddenException("Cannot modify notification belonging to another user");
        }

        if (!notification.getIsRead()) {
            notification.setIsRead(true);
            notification.setReadAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);
            log.info("Marked notification {} as read for user {}", notificationUuid, userUuid);
        }

        return mapToDto(notification);
    }

    @Override
    @Transactional
    public int markAllAsRead(String userUuid) {
        int updated = notificationRepository.markAllAsReadForUser(userUuid, LocalDateTime.now());
        log.info("Marked {} notifications as read for user {}", updated, userUuid);
        return updated;
    }

    @Override
    @Transactional
    public NotificationDto dispatchNotification(DirectNotificationRequest request) {
        // 1. Check Idempotency Key if provided
        if (request.getIdempotencyKey() != null && notificationRepository.existsByIdempotencyKey(request.getIdempotencyKey())) {
            log.info("Suppressed duplicate notification dispatch with idempotency key: {}", request.getIdempotencyKey());
            // Return existing or mock response
        }

        // 2. Persist in-app notification record
        NotificationEntity notification = NotificationEntity.builder()
                .notificationUuid(UUID.randomUUID().toString())
                .recipientUuid(request.getRecipientUuid())
                .recipientRole(request.getRecipientRole())
                .notificationType(request.getNotificationType())
                .category(request.getCategory())
                .title(request.getTitle())
                .body(request.getBody())
                .deepLink(request.getDeepLink())
                .relatedEntityType(request.getRelatedEntityType())
                .relatedEntityUuid(request.getRelatedEntityUuid())
                .status(NotificationStatus.PROCESSING)
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        NotificationEntity saved = notificationRepository.save(notification);

        // 3. Resolve user preferences
        NotificationPreferenceEntity preferences = preferenceService.getOrCreateEntity(request.getRecipientUuid());

        List<NotificationChannel> channels = request.getChannels();
        if (channels == null || channels.isEmpty()) {
            channels = List.of(NotificationChannel.IN_APP, NotificationChannel.PUSH);
        }

        // In-App Delivery (always recorded)
        if (channels.contains(NotificationChannel.IN_APP)) {
            NotificationDeliveryEntity inAppDelivery = NotificationDeliveryEntity.builder()
                    .deliveryUuid(UUID.randomUUID().toString())
                    .notificationUuid(saved.getNotificationUuid())
                    .channel(NotificationChannel.IN_APP)
                    .provider("IN_APP")
                    .recipientDestination(saved.getRecipientUuid())
                    .status(DeliveryStatus.DELIVERED)
                    .completedAt(LocalDateTime.now())
                    .build();
            deliveryRepository.save(inAppDelivery);
        }

        // Push / FCM Delivery
        if (channels.contains(NotificationChannel.PUSH) && preferences.getPushEnabled()) {
            List<NotificationDeviceEntity> activeDevices = deviceService.getActiveDeviceEntities(request.getRecipientUuid());
            for (NotificationDeviceEntity device : activeDevices) {
                ProviderDispatchResult result = pushProvider.sendPushNotification(
                        device.getFcmToken(),
                        device.getPlatform(),
                        request.getTitle(),
                        request.getBody(),
                        request.getDeepLink(),
                        Map.of("notificationUuid", saved.getNotificationUuid(), "type", request.getNotificationType())
                );

                NotificationDeliveryEntity delivery = NotificationDeliveryEntity.builder()
                        .deliveryUuid(UUID.randomUUID().toString())
                        .notificationUuid(saved.getNotificationUuid())
                        .channel(NotificationChannel.PUSH)
                        .provider(pushProvider.getProviderName())
                        .recipientDestination(maskToken(device.getFcmToken()))
                        .status(result.getStatus())
                        .providerMessageId(result.getProviderMessageId())
                        .errorCode(result.getErrorCode())
                        .errorMessage(result.getErrorMessage())
                        .completedAt(result.isSuccessful() ? LocalDateTime.now() : null)
                        .build();
                deliveryRepository.save(delivery);

                // Auto-deactivate token if provider reports it unregistered
                if (!result.isSuccessful() && pushProvider.isTokenInvalidError(result.getErrorCode())) {
                    deviceService.deactivateInvalidToken(device.getFcmToken());
                }
            }
        }

        // Update Notification status to COMPLETED
        saved.setStatus(NotificationStatus.COMPLETED);
        NotificationEntity finalized = notificationRepository.save(saved);

        NotificationDto dto = mapToDto(finalized);

        // Notify active real-time SSE subscriber
        realtimeNotifier.notifyUser(request.getRecipientUuid(), dto);

        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> adminListNotifications(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NotificationEntity> entityPage = notificationRepository.findAll(pageable);
        List<NotificationDto> dtos = entityPage.getContent().stream().map(this::mapToDto).collect(Collectors.toList());

        return PageResponse.<NotificationDto>builder()
                .content(dtos)
                .pageNumber(entityPage.getNumber())
                .pageSize(entityPage.getSize())
                .totalElements(entityPage.getTotalElements())
                .totalPages(entityPage.getTotalPages())
                .last(entityPage.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryAuditDto> adminGetDeliveryAudit(String notificationUuid) {
        return deliveryRepository.findByNotificationUuid(notificationUuid).stream()
                .map(d -> DeliveryAuditDto.builder()
                        .deliveryUuid(d.getDeliveryUuid())
                        .notificationUuid(d.getNotificationUuid())
                        .channel(d.getChannel())
                        .provider(d.getProvider())
                        .maskedDestination(d.getRecipientDestination())
                        .attemptNumber(d.getAttemptNumber())
                        .providerMessageId(d.getProviderMessageId())
                        .status(d.getStatus())
                        .errorCode(d.getErrorCode())
                        .errorMessage(d.getErrorMessage())
                        .attemptedAt(d.getAttemptedAt())
                        .completedAt(d.getCompletedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public NotificationDto adminRetryDelivery(String notificationUuid) {
        NotificationEntity notification = notificationRepository.findByNotificationUuid(notificationUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationUuid));

        List<NotificationDeliveryEntity> deliveries = deliveryRepository.findByNotificationUuid(notificationUuid);
        for (NotificationDeliveryEntity delivery : deliveries) {
            if (delivery.getStatus() == DeliveryStatus.FAILED || delivery.getStatus() == DeliveryStatus.RETRY_SCHEDULED) {
                delivery.setAttemptNumber(delivery.getAttemptNumber() + 1);
                delivery.setStatus(DeliveryStatus.ACCEPTED);
                delivery.setErrorMessage(null);
                delivery.setErrorCode(null);
                delivery.setCompletedAt(LocalDateTime.now());
                deliveryRepository.save(delivery);
            }
        }

        notification.setStatus(NotificationStatus.COMPLETED);
        NotificationEntity updated = notificationRepository.save(notification);
        log.info("Admin retried notification deliveries for {}", notificationUuid);
        return mapToDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationMetricsResponse adminGetMetrics() {
        long totalNotifs = notificationRepository.count();
        long totalDelivs = deliveryRepository.count();
        long delivered = deliveryRepository.countByStatus(DeliveryStatus.DELIVERED);
        long failed = deliveryRepository.countByStatus(DeliveryStatus.FAILED)
                + deliveryRepository.countByStatus(DeliveryStatus.PERMANENTLY_FAILED);
        long pending = deliveryRepository.countByStatus(DeliveryStatus.PENDING)
                + deliveryRepository.countByStatus(DeliveryStatus.RETRY_SCHEDULED);
        long activeDevs = deviceRepository.countByIsActiveTrue();
        long deadLetter = eventInboxRepository.countByStatus(EventInboxStatus.DEAD_LETTER);

        Map<String, Long> breakdown = new HashMap<>();
        for (NotificationChannel channel : NotificationChannel.values()) {
            breakdown.put(channel.name(), deliveryRepository.countByChannel(channel));
        }

        return NotificationMetricsResponse.builder()
                .totalNotifications(totalNotifs)
                .totalDeliveries(totalDelivs)
                .deliveredCount(delivered)
                .failedCount(failed)
                .pendingCount(pending)
                .activeDevices(activeDevs)
                .deadLetterEvents(deadLetter)
                .channelBreakdown(breakdown)
                .build();
    }

    private NotificationDto mapToDto(NotificationEntity entity) {
        return NotificationDto.builder()
                .notificationUuid(entity.getNotificationUuid())
                .recipientUuid(entity.getRecipientUuid())
                .recipientRole(entity.getRecipientRole())
                .notificationType(entity.getNotificationType())
                .category(entity.getCategory())
                .title(entity.getTitle())
                .body(entity.getBody())
                .deepLink(entity.getDeepLink())
                .relatedEntityType(entity.getRelatedEntityType())
                .relatedEntityUuid(entity.getRelatedEntityUuid())
                .isRead(entity.getIsRead())
                .readAt(entity.getReadAt())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 10) return "***";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}
