package com.lexor.feedback.service.impl;

import com.lexor.feedback.dto.response.DriverRatingSummaryResponse;
import com.lexor.feedback.dto.response.RiderRatingSummaryResponse;
import com.lexor.feedback.entity.DriverRatingSummary;
import com.lexor.feedback.entity.RideFeedback;
import com.lexor.feedback.entity.RiderRatingSummary;
import com.lexor.feedback.enums.FeedbackRole;
import com.lexor.feedback.enums.ModerationStatus;
import com.lexor.feedback.repository.DriverRatingSummaryRepository;
import com.lexor.feedback.repository.RideFeedbackRepository;
import com.lexor.feedback.repository.RiderRatingSummaryRepository;
import com.lexor.feedback.service.IRatingCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RatingCalculationServiceImpl implements IRatingCalculationService {

    private final RideFeedbackRepository feedbackRepository;
    private final DriverRatingSummaryRepository driverSummaryRepository;
    private final RiderRatingSummaryRepository riderSummaryRepository;

    @Override
    @Transactional
    public void updateRatingSummaryOnNewFeedback(RideFeedback feedback) {
        if (feedback == null) return;
        recalculateSummaryForTarget(feedback.getTargetUuid(), feedback.getTargetRole());
    }

    @Override
    @Transactional
    public void updateRatingSummaryOnRatingEdit(RideFeedback feedback, int oldRating) {
        if (feedback == null) return;
        recalculateSummaryForTarget(feedback.getTargetUuid(), feedback.getTargetRole());
    }

    @Override
    @Transactional
    public void recalculateSummaryForTarget(String targetUuid, FeedbackRole targetRole) {
        if (targetUuid == null || targetRole == null) return;

        // Fetch all VISIBLE reviews for this target
        List<RideFeedback> visibleReviews = feedbackRepository
                .findByTargetUuidAndModerationStatus(targetUuid, ModerationStatus.VISIBLE);

        int totalCount = visibleReviews.size();
        int star1 = 0, star2 = 0, star3 = 0, star4 = 0, star5 = 0;
        int ratingSum = 0;

        for (RideFeedback rf : visibleReviews) {
            int r = rf.getRating();
            ratingSum += r;
            switch (r) {
                case 1 -> star1++;
                case 2 -> star2++;
                case 3 -> star3++;
                case 4 -> star4++;
                case 5 -> star5++;
            }
        }

        BigDecimal averageRating = totalCount > 0
                ? BigDecimal.valueOf(ratingSum)
                        .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP)
                : new BigDecimal("5.00");

        long excludedCount = feedbackRepository.countByTargetUuidAndModerationStatusNot(targetUuid, ModerationStatus.VISIBLE);

        if (targetRole == FeedbackRole.DRIVER) {
            DriverRatingSummary summary = driverSummaryRepository.findByDriverUuid(targetUuid)
                    .orElseGet(() -> DriverRatingSummary.builder()
                            .driverUuid(targetUuid)
                            .totalCompletedRides(totalCount)
                            .build());

            summary.setTotalRatings(totalCount);
            summary.setAverageRating(averageRating);
            summary.setOneStarCount(star1);
            summary.setTwoStarCount(star2);
            summary.setThreeStarCount(star3);
            summary.setFourStarCount(star4);
            summary.setFiveStarCount(star5);
            summary.setExcludedRatingsCount((int) excludedCount);
            if (summary.getTotalCompletedRides() < totalCount) {
                summary.setTotalCompletedRides(totalCount);
            }
            summary.setLastRecalculatedAt(LocalDateTime.now());
            driverSummaryRepository.save(summary);
            log.info("Recalculated rating summary for driver {}: avg={}, total={}", targetUuid, averageRating, totalCount);

        } else if (targetRole == FeedbackRole.RIDER) {
            RiderRatingSummary summary = riderSummaryRepository.findByRiderUuid(targetUuid)
                    .orElseGet(() -> RiderRatingSummary.builder()
                            .riderUuid(targetUuid)
                            .totalCompletedRides(totalCount)
                            .build());

            summary.setTotalRatings(totalCount);
            summary.setAverageRating(averageRating);
            summary.setOneStarCount(star1);
            summary.setTwoStarCount(star2);
            summary.setThreeStarCount(star3);
            summary.setFourStarCount(star4);
            summary.setFiveStarCount(star5);
            summary.setExcludedRatingsCount((int) excludedCount);
            if (summary.getTotalCompletedRides() < totalCount) {
                summary.setTotalCompletedRides(totalCount);
            }
            summary.setLastRecalculatedAt(LocalDateTime.now());
            riderSummaryRepository.save(summary);
            log.info("Recalculated rating summary for rider {}: avg={}, total={}", targetUuid, averageRating, totalCount);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DriverRatingSummaryResponse getDriverSummary(String driverUuid) {
        return driverSummaryRepository.findByDriverUuid(driverUuid)
                .map(DriverRatingSummaryResponse::fromEntity)
                .orElseGet(() -> {
                    // Fallback to fresh computation if summary record does not yet exist
                    recalculateSummaryForTarget(driverUuid, FeedbackRole.DRIVER);
                    return driverSummaryRepository.findByDriverUuid(driverUuid)
                            .map(DriverRatingSummaryResponse::fromEntity)
                            .orElseGet(() -> DriverRatingSummaryResponse.builder()
                                    .driverUuid(driverUuid)
                                    .averageRating(new BigDecimal("5.00"))
                                    .totalRatings(0)
                                    .excludedRatingsCount(0)
                                    .totalCompletedRides(0)
                                    .recentTrend("NEW")
                                    .lastRecalculatedAt(LocalDateTime.now())
                                    .build());
                });
    }

    @Override
    @Transactional(readOnly = true)
    public RiderRatingSummaryResponse getRiderSummary(String riderUuid) {
        return riderSummaryRepository.findByRiderUuid(riderUuid)
                .map(RiderRatingSummaryResponse::fromEntity)
                .orElseGet(() -> {
                    recalculateSummaryForTarget(riderUuid, FeedbackRole.RIDER);
                    return riderSummaryRepository.findByRiderUuid(riderUuid)
                            .map(RiderRatingSummaryResponse::fromEntity)
                            .orElseGet(() -> RiderRatingSummaryResponse.builder()
                                    .riderUuid(riderUuid)
                                    .averageRating(new BigDecimal("5.00"))
                                    .totalRatings(0)
                                    .excludedRatingsCount(0)
                                    .totalCompletedRides(0)
                                    .lastRecalculatedAt(LocalDateTime.now())
                                    .build());
                });
    }
}
