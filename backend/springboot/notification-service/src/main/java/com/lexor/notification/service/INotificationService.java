package com.lexor.notification.service;

import com.lexor.notification.dto.request.DirectNotificationRequest;
import com.lexor.notification.dto.response.*;
import com.lexor.notification.enums.NotificationCategory;

import java.util.List;

public interface INotificationService {

    PageResponse<NotificationDto> getUserInbox(
            String userUuid,
            Boolean isRead,
            NotificationCategory category,
            int page,
            int size
    );

    UnreadCountResponse getUnreadCount(String userUuid);

    NotificationDto getNotification(String userUuid, String notificationUuid);

    NotificationDto markAsRead(String userUuid, String notificationUuid);

    int markAllAsRead(String userUuid);

    NotificationDto dispatchNotification(DirectNotificationRequest request);

    // Admin endpoints
    PageResponse<NotificationDto> adminListNotifications(int page, int size);

    List<DeliveryAuditDto> adminGetDeliveryAudit(String notificationUuid);

    NotificationDto adminRetryDelivery(String notificationUuid);

    NotificationMetricsResponse adminGetMetrics();
}
