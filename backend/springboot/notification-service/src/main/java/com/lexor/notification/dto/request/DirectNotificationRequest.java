package com.lexor.notification.dto.request;

import com.lexor.notification.enums.NotificationCategory;
import com.lexor.notification.enums.NotificationChannel;
import com.lexor.notification.enums.RecipientRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectNotificationRequest {

    @NotBlank(message = "Recipient UUID is required")
    private String recipientUuid;

    @NotNull(message = "Recipient role is required")
    private RecipientRole recipientRole;

    @NotBlank(message = "Notification type is required")
    private String notificationType;

    @NotNull(message = "Category is required")
    private NotificationCategory category;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Body is required")
    private String body;

    private String deepLink;
    private String relatedEntityType;
    private String relatedEntityUuid;
    private String idempotencyKey;

    private List<NotificationChannel> channels;
}
