package com.lexor.notification.dto.response;

import com.lexor.notification.enums.DeliveryStatus;
import com.lexor.notification.enums.NotificationChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryAuditDto {
    private String deliveryUuid;
    private String notificationUuid;
    private NotificationChannel channel;
    private String provider;
    private String maskedDestination;
    private Integer attemptNumber;
    private String providerMessageId;
    private DeliveryStatus status;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime attemptedAt;
    private LocalDateTime completedAt;
}
