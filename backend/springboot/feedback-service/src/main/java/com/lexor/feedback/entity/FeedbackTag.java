package com.lexor.feedback.entity;

import com.lexor.feedback.enums.TagCategory;
import com.lexor.feedback.enums.TagTargetRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "feedback_tags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tag_code", nullable = false, unique = true, length = 50)
    private String tagCode;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_role", nullable = false)
    @Builder.Default
    private TagTargetRole targetRole = TagTargetRole.BOTH;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    @Builder.Default
    private TagCategory category = TagCategory.GENERAL;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
