package com.lexor.notification.entity;

import com.lexor.notification.enums.NotificationCategory;
import com.lexor.notification.enums.NotificationStatus;
import com.lexor.notification.enums.RecipientRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notif_uuid", columnList = "notificationUuid"),
    @Index(name = "idx_notif_recipient", columnList = "recipientUuid, isRead, createdAt"),
    @Index(name = "idx_notif_category", columnList = "category"),
    @Index(name = "idx_notif_type", columnList = "notificationType"),
    @Index(name = "idx_notif_status", columnList = "status"),
    @Index(name = "idx_notif_source_event", columnList = "sourceEventId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_uuid", nullable = false, unique = true, length = 36)
    private String notificationUuid;

    @Column(name = "recipient_uuid", nullable = false, length = 36)
    private String recipientUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_role", nullable = false, length = 20)
    private RecipientRole recipientRole;

    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private NotificationCategory category;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "deep_link", length = 500)
    private String deepLink;

    @Column(name = "related_entity_type", length = 50)
    private String relatedEntityType;

    @Column(name = "related_entity_uuid", length = 36)
    private String relatedEntityUuid;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.CREATED;

    @Column(name = "source_event_id", length = 100)
    private String sourceEventId;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
