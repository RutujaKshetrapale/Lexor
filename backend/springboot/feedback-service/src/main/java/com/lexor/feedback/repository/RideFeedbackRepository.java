package com.lexor.feedback.repository;

import com.lexor.feedback.entity.RideFeedback;
import com.lexor.feedback.enums.FeedbackRole;
import com.lexor.feedback.enums.ModerationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RideFeedbackRepository extends JpaRepository<RideFeedback, Long>, JpaSpecificationExecutor<RideFeedback> {

    Optional<RideFeedback> findByFeedbackUuid(String feedbackUuid);

    Optional<RideFeedback> findByRideUuidAndReviewerUuidAndReviewerRole(
            String rideUuid, String reviewerUuid, FeedbackRole reviewerRole);

    boolean existsByRideUuidAndReviewerUuidAndReviewerRole(
            String rideUuid, String reviewerUuid, FeedbackRole reviewerRole);

    Page<RideFeedback> findByReviewerUuidOrderByCreatedAtDesc(String reviewerUuid, Pageable pageable);

    Page<RideFeedback> findByTargetUuidAndModerationStatusOrderByCreatedAtDesc(
            String targetUuid, ModerationStatus moderationStatus, Pageable pageable);

    List<RideFeedback> findByTargetUuidAndModerationStatus(
            String targetUuid, ModerationStatus moderationStatus);

    List<RideFeedback> findByTargetUuid(String targetUuid);

    long countByTargetUuidAndModerationStatus(String targetUuid, ModerationStatus moderationStatus);

    long countByTargetUuidAndModerationStatusNot(String targetUuid, ModerationStatus moderationStatus);

    long countByModerationStatus(ModerationStatus moderationStatus);

    @Query("SELECT f.rating, COUNT(f) FROM RideFeedback f WHERE f.targetUuid = :targetUuid AND f.moderationStatus = :status GROUP BY f.rating")
    List<Object[]> countRatingsByStarForTarget(
            @Param("targetUuid") String targetUuid,
            @Param("status") ModerationStatus status);

    @Query("SELECT f.rating, COUNT(f) FROM RideFeedback f WHERE f.moderationStatus = 'VISIBLE' GROUP BY f.rating")
    List<Object[]> countAllVisibleRatingsByStar();

    @Query("SELECT AVG(f.rating) FROM RideFeedback f WHERE f.moderationStatus = 'VISIBLE'")
    Double getOverallPlatformAverage();
}
