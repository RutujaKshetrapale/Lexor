package com.lexor.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "notification_preferences", indexes = {
    @Index(name = "idx_pref_uuid", columnList = "preferenceUuid"),
    @Index(name = "idx_pref_user", columnList = "userUuid")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "preference_uuid", nullable = false, unique = true, length = 36)
    private String preferenceUuid;

    @Column(name = "user_uuid", nullable = false, unique = true, length = 36)
    private String userUuid;

    @Column(name = "push_enabled", nullable = false)
    @Builder.Default
    private Boolean pushEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    @Builder.Default
    private Boolean emailEnabled = true;

    @Column(name = "sms_enabled", nullable = false)
    @Builder.Default
    private Boolean smsEnabled = true;

    @Column(name = "ride_updates_enabled", nullable = false)
    @Builder.Default
    private Boolean rideUpdatesEnabled = true;

    @Column(name = "payment_updates_enabled", nullable = false)
    @Builder.Default
    private Boolean paymentUpdatesEnabled = true;

    @Column(name = "promotional_enabled", nullable = false)
    @Builder.Default
    private Boolean promotionalEnabled = false;

    // Security alerts cannot be disabled by user preference
    @Column(name = "security_alerts_enabled", nullable = false)
    @Builder.Default
    private Boolean securityAlertsEnabled = true;

    @Column(name = "quiet_hours_enabled", nullable = false)
    @Builder.Default
    private Boolean quietHoursEnabled = false;

    @Column(name = "quiet_hours_start")
    @Builder.Default
    private LocalTime quietHoursStart = LocalTime.of(22, 0);

    @Column(name = "quiet_hours_end")
    @Builder.Default
    private LocalTime quietHoursEnd = LocalTime.of(7, 0);

    @Column(name = "preferred_locale", nullable = false, length = 10)
    @Builder.Default
    private String preferredLocale = "en";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
