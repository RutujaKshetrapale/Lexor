package com.lexor.feedback.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectNotificationRequest {

    private String recipientUuid;
    private String recipientRole; // RIDER, DRIVER, ADMIN
    private String notificationType;
    private String category; // RIDE, SYSTEM
    private String title;
    private String body;
    private String deepLink;
    private String relatedEntityType;
    private String relatedEntityUuid;
    private String idempotencyKey;
    private List<String> channels;
}
