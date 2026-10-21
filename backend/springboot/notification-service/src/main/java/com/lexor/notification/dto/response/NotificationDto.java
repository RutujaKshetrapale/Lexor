package com.lexor.notification.dto.response;

import com.lexor.notification.enums.NotificationCategory;
import com.lexor.notification.enums.NotificationStatus;
import com.lexor.notification.enums.RecipientRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private String notificationUuid;
    private String recipientUuid;
    private RecipientRole recipientRole;
    private String notificationType;
    private NotificationCategory category;
    private String title;
    private String body;
    private String deepLink;
    private String relatedEntityType;
    private String relatedEntityUuid;
    private Boolean isRead;
    private LocalDateTime readAt;
    private NotificationStatus status;
    private LocalDateTime createdAt;
}
