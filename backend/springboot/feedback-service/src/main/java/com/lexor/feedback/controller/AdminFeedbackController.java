package com.lexor.feedback.controller;

import com.lexor.feedback.dto.request.ModerationActionRequest;
import com.lexor.feedback.dto.response.*;
import com.lexor.feedback.enums.ModerationStatus;
import com.lexor.feedback.enums.ReportStatus;
import com.lexor.feedback.security.UserPrincipal;
import com.lexor.feedback.service.IFeedbackService;
import com.lexor.feedback.service.IModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/feedback")
@RequiredArgsConstructor
@Tag(name = "Administrator Feedback Management", description = "Endpoints for platform administrators to inspect reviews, review abuse reports, execute moderation actions, and view analytics")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFeedbackController {

    private final IFeedbackService feedbackService;
    private final IModerationService moderationService;

    @Operation(summary = "Admin: List and search platform reviews with filtering")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<FeedbackResponse>>> getAdminFeedback(
            @RequestParam(required = false) String reviewerUuid,
            @RequestParam(required = false) String targetUuid,
            @RequestParam(required = false) ModerationStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<FeedbackResponse> response = feedbackService.getAdminFeedback(reviewerUuid, targetUuid, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Admin: Get detailed review information by UUID")
    @GetMapping("/{reviewUuid}")
    public ResponseEntity<ApiResponse<FeedbackResponse>> getAdminFeedbackByUuid(
            @PathVariable String reviewUuid
    ) {
        FeedbackResponse response = feedbackService.getAdminFeedbackByUuid(reviewUuid);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Admin: List submitted review abuse reports")
    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<PageResponse<FeedbackReportResponse>>> getReports(
            @RequestParam(required = false) ReportStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<FeedbackReportResponse> response = moderationService.getReports(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Admin: Apply moderation action to a review (with required audit reason)")
    @PostMapping("/{reviewUuid}/moderation")
    public ResponseEntity<ApiResponse<FeedbackResponse>> applyModerationAction(
            @AuthenticationPrincipal UserPrincipal moderator,
            @PathVariable String reviewUuid,
            @Valid @RequestBody ModerationActionRequest request
    ) {
        FeedbackResponse response = moderationService.applyModerationAction(moderator, reviewUuid, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Moderation action executed successfully"));
    }

    @Operation(summary = "Admin: Get moderation history for a review")
    @GetMapping("/{reviewUuid}/history")
    public ResponseEntity<ApiResponse<List<ModerationHistoryResponse>>> getModerationHistory(
            @PathVariable String reviewUuid
    ) {
        List<ModerationHistoryResponse> history = moderationService.getModerationHistory(reviewUuid);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @Operation(summary = "Admin: Platform rating and feedback analytics")
    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<FeedbackAnalyticsResponse>> getAnalytics() {
        FeedbackAnalyticsResponse analytics = moderationService.getAnalytics();
        return ResponseEntity.ok(ApiResponse.success(analytics));
    }
}
