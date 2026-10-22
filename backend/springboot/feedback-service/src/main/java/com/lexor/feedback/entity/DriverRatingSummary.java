package com.lexor.feedback.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "driver_rating_summaries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverRatingSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "driver_uuid", nullable = false, unique = true, length = 36)
    private String driverUuid;

    @Column(name = "total_ratings", nullable = false)
    @Builder.Default
    private Integer totalRatings = 0;

    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal averageRating = new BigDecimal("5.00");

    @Column(name = "five_star_count", nullable = false)
    @Builder.Default
    private Integer fiveStarCount = 0;

    @Column(name = "four_star_count", nullable = false)
    @Builder.Default
    private Integer fourStarCount = 0;

    @Column(name = "three_star_count", nullable = false)
    @Builder.Default
    private Integer threeStarCount = 0;

    @Column(name = "two_star_count", nullable = false)
    @Builder.Default
    private Integer twoStarCount = 0;

    @Column(name = "one_star_count", nullable = false)
    @Builder.Default
    private Integer oneStarCount = 0;

    @Column(name = "excluded_ratings_count", nullable = false)
    @Builder.Default
    private Integer excludedRatingsCount = 0;

    @Column(name = "total_completed_rides", nullable = false)
    @Builder.Default
    private Integer totalCompletedRides = 0;

    @UpdateTimestamp
    @Column(name = "last_recalculated_at", nullable = false)
    private LocalDateTime lastRecalculatedAt;
}
