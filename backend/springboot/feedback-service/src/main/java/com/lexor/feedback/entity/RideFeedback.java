package com.lexor.feedback.entity;

import com.lexor.feedback.enums.FeedbackRole;
import com.lexor.feedback.enums.ModerationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "ride_feedback",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ride_reviewer_direction", columnNames = {"ride_uuid", "reviewer_uuid", "reviewer_role"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feedback_uuid", nullable = false, unique = true, length = 36)
    private String feedbackUuid;

    @Column(name = "ride_uuid", nullable = false, length = 36)
    private String rideUuid;

    @Column(name = "booking_uuid", length = 36)
    private String bookingUuid;

    @Column(name = "reviewer_uuid", nullable = false, length = 36)
    private String reviewerUuid;

    @Column(name = "target_uuid", nullable = false, length = 36)
    private String targetUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "reviewer_role", nullable = false)
    private FeedbackRole reviewerRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_role", nullable = false)
    private FeedbackRole targetRole;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "review_text", length = 1000)
    private String reviewText;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false)
    @Builder.Default
    private ModerationStatus moderationStatus = ModerationStatus.VISIBLE;

    @Column(name = "is_edited", nullable = false)
    @Builder.Default
    private Boolean isEdited = false;

    @Column(name = "editable_until")
    private LocalDateTime editableUntil;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "feedback_tag_assignments",
            joinColumns = @JoinColumn(name = "feedback_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Builder.Default
    private Set<FeedbackTag> tags = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
