package com.lexor.notification.entity;

import com.lexor.notification.enums.DeliveryStatus;
import com.lexor.notification.enums.NotificationChannel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_deliveries", indexes = {
    @Index(name = "idx_deliv_uuid", columnList = "deliveryUuid"),
    @Index(name = "idx_deliv_notif", columnList = "notificationUuid"),
    @Index(name = "idx_deliv_channel", columnList = "channel"),
    @Index(name = "idx_deliv_status", columnList = "status"),
    @Index(name = "idx_deliv_provider_msg", columnList = "providerMessageId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDeliveryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_uuid", nullable = false, unique = true, length = 36)
    private String deliveryUuid;

    @Column(name = "notification_uuid", nullable = false, length = 36)
    private String notificationUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "recipient_destination", nullable = false, length = 255)
    private String recipientDestination;

    @Column(name = "attempt_number", nullable = false)
    @Builder.Default
    private Integer attemptNumber = 1;

    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "attempted_at", nullable = false)
    @Builder.Default
    private LocalDateTime attemptedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
