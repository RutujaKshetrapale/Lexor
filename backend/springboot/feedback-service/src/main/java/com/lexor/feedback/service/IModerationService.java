package com.lexor.feedback.service;

import com.lexor.feedback.dto.request.ModerationActionRequest;
import com.lexor.feedback.dto.request.ReportReviewRequest;
import com.lexor.feedback.dto.response.*;
import com.lexor.feedback.enums.ReportStatus;
import com.lexor.feedback.security.UserPrincipal;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface IModerationService {

    FeedbackReportResponse reportReview(UserPrincipal reporter, String reviewUuid, ReportReviewRequest request);

    PageResponse<FeedbackReportResponse> getReports(ReportStatus status, Pageable pageable);

    FeedbackResponse applyModerationAction(UserPrincipal moderator, String reviewUuid, ModerationActionRequest request);

    List<ModerationHistoryResponse> getModerationHistory(String reviewUuid);

    FeedbackAnalyticsResponse getAnalytics();
}
