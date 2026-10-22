package com.lexor.feedback.entity;

import com.lexor.feedback.enums.ModerationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "feedback_moderation_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackModerationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feedback_id", nullable = false)
    private RideFeedback feedback;

    @Column(name = "moderator_uuid", nullable = false, length = 36)
    private String moderatorUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false)
    private ModerationStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private ModerationStatus newStatus;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @CreationTimestamp
    @Column(name = "moderated_at", nullable = false, updatable = false)
    private LocalDateTime moderatedAt;
}
