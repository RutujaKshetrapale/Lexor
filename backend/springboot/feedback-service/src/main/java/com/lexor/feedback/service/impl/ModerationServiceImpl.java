package com.lexor.feedback.service.impl;

import com.lexor.feedback.config.FeedbackProperties;
import com.lexor.feedback.dto.request.ModerationActionRequest;
import com.lexor.feedback.dto.request.ReportReviewRequest;
import com.lexor.feedback.dto.response.*;
import com.lexor.feedback.entity.FeedbackModerationHistory;
import com.lexor.feedback.entity.FeedbackReport;
import com.lexor.feedback.entity.RideFeedback;
import com.lexor.feedback.enums.ModerationStatus;
import com.lexor.feedback.enums.ReportStatus;
import com.lexor.feedback.exception.BadRequestException;
import com.lexor.feedback.exception.ConflictException;
import com.lexor.feedback.exception.ResourceNotFoundException;
import com.lexor.feedback.repository.FeedbackModerationHistoryRepository;
import com.lexor.feedback.repository.FeedbackReportRepository;
import com.lexor.feedback.repository.RideFeedbackRepository;
import com.lexor.feedback.security.UserPrincipal;
import com.lexor.feedback.service.IModerationService;
import com.lexor.feedback.service.IRatingCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationServiceImpl implements IModerationService {

    private final RideFeedbackRepository feedbackRepository;
    private final FeedbackReportRepository reportRepository;
    private final FeedbackModerationHistoryRepository historyRepository;
    private final IRatingCalculationService ratingCalculationService;
    private final FeedbackProperties properties;

    @Override
    @Transactional
    public FeedbackReportResponse reportReview(UserPrincipal reporter, String reviewUuid, ReportReviewRequest request) {
        RideFeedback feedback = feedbackRepository.findByFeedbackUuid(reviewUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with UUID: " + reviewUuid));

        if (reportRepository.existsByFeedbackIdAndReporterUuid(feedback.getId(), reporter.getUserId())) {
            throw new ConflictException("You have already reported this review.");
        }

        FeedbackReport report = FeedbackReport.builder()
                .reportUuid(UUID.randomUUID().toString())
                .feedback(feedback)
                .reporterUuid(reporter.getUserId())
                .reason(request.getReason())
                .comments(request.getComments())
                .status(ReportStatus.PENDING)
                .build();

        FeedbackReport savedReport = reportRepository.save(report);

        // Check if report threshold reached for automatic UNDER_REVIEW
        long reportCount = reportRepository.countByFeedbackId(feedback.getId());
        if (reportCount >= properties.getPolicy().getReportAutoReviewThreshold()
                && feedback.getModerationStatus() == ModerationStatus.VISIBLE) {
            log.info("Auto-moderating review {} to UNDER_REVIEW due to {} reports", reviewUuid, reportCount);
            feedback.setModerationStatus(ModerationStatus.UNDER_REVIEW);
            feedbackRepository.save(feedback);

            historyRepository.save(FeedbackModerationHistory.builder()
                    .feedback(feedback)
                    .moderatorUuid("SYSTEM_AUTO_MODERATION")
                    .previousStatus(ModerationStatus.VISIBLE)
                    .newStatus(ModerationStatus.UNDER_REVIEW)
                    .reason("Automated review flag triggered: exceeded report threshold (" + reportCount + ")")
                    .build());

            // Recalculate target's rating summary because review is no longer VISIBLE
            ratingCalculationService.recalculateSummaryForTarget(feedback.getTargetUuid(), feedback.getTargetRole());
        }

        return FeedbackReportResponse.fromEntity(savedReport);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FeedbackReportResponse> getReports(ReportStatus status, Pageable pageable) {
        Page<FeedbackReport> page = status != null
                ? reportRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                : reportRepository.findAllByOrderByCreatedAtDesc(pageable);

        List<FeedbackReportResponse> content = page.getContent().stream()
                .map(FeedbackReportResponse::fromEntity)
                .collect(Collectors.toList());

        return PageResponse.<FeedbackReportResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional
    public FeedbackResponse applyModerationAction(UserPrincipal moderator, String reviewUuid, ModerationActionRequest request) {
        RideFeedback feedback = feedbackRepository.findByFeedbackUuid(reviewUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with UUID: " + reviewUuid));

        ModerationStatus oldStatus = feedback.getModerationStatus();
        ModerationStatus newStatus = request.getNewStatus();

        if (oldStatus == newStatus) {
            throw new BadRequestException("Review is already in status: " + newStatus);
        }

        if (!oldStatus.canTransitionTo(newStatus)) {
            throw new BadRequestException("Invalid moderation transition from " + oldStatus + " to " + newStatus);
        }

        feedback.setModerationStatus(newStatus);
        RideFeedback updated = feedbackRepository.save(feedback);

        historyRepository.save(FeedbackModerationHistory.builder()
                .feedback(updated)
                .moderatorUuid(moderator.getUserId())
                .previousStatus(oldStatus)
                .newStatus(newStatus)
                .reason(request.getReason())
                .build());

        log.info("Moderator {} updated review {} from {} to {}. Reason: {}",
                moderator.getUserId(), reviewUuid, oldStatus, newStatus, request.getReason());

        // Recalculate target's rating summary if visibility changed
        if (oldStatus == ModerationStatus.VISIBLE || newStatus == ModerationStatus.VISIBLE) {
            ratingCalculationService.recalculateSummaryForTarget(updated.getTargetUuid(), updated.getTargetRole());
        }

        return FeedbackResponse.fromEntity(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModerationHistoryResponse> getModerationHistory(String reviewUuid) {
        RideFeedback feedback = feedbackRepository.findByFeedbackUuid(reviewUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with UUID: " + reviewUuid));

        return historyRepository.findByFeedbackIdOrderByModeratedAtDesc(feedback.getId())
                .stream()
                .map(ModerationHistoryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackAnalyticsResponse getAnalytics() {
        long total = feedbackRepository.count();
        long visible = feedbackRepository.countByModerationStatus(ModerationStatus.VISIBLE);
        long underReview = feedbackRepository.countByModerationStatus(ModerationStatus.UNDER_REVIEW);
        long hidden = feedbackRepository.countByModerationStatus(ModerationStatus.HIDDEN);
        long removed = feedbackRepository.countByModerationStatus(ModerationStatus.REMOVED);

        Double avgPlatform = feedbackRepository.getOverallPlatformAverage();
        BigDecimal platformAvg = avgPlatform != null
                ? BigDecimal.valueOf(avgPlatform).setScale(2, RoundingMode.HALF_UP)
                : new BigDecimal("5.00");

        Map<Integer, Long> dist = new LinkedHashMap<>();
        for (int i = 5; i >= 1; i--) {
            dist.put(i, 0L);
        }

        List<Object[]> ratingCounts = feedbackRepository.countAllVisibleRatingsByStar();
        for (Object[] row : ratingCounts) {
            if (row[0] instanceof Number r && row[1] instanceof Number count) {
                dist.put(r.intValue(), count.longValue());
            }
        }

        return FeedbackAnalyticsResponse.builder()
                .totalFeedbackCount(total)
                .visibleFeedbackCount(visible)
                .underReviewFeedbackCount(underReview)
                .hiddenFeedbackCount(hidden)
                .removedFeedbackCount(removed)
                .platformAverageRating(platformAvg)
                .ratingsDistribution(dist)
                .topTags(Collections.emptyList())
                .build();
    }
}
