package com.lexor.feedback.controller;

import com.lexor.feedback.dto.request.ReportReviewRequest;
import com.lexor.feedback.dto.request.SubmitRatingRequest;
import com.lexor.feedback.dto.request.UpdateRatingRequest;
import com.lexor.feedback.dto.response.*;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
@Tag(name = "Feedback & Ratings", description = "Endpoints for submitting trip ratings, querying feedback history, reporting reviews, and retrieving tags")
public class FeedbackController {

    private final IFeedbackService feedbackService;
    private final IModerationService moderationService;

    @Operation(summary = "Rider: Rate assigned driver for a completed ride")
    @PostMapping("/rides/{rideUuid}/driver-rating")
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<ApiResponse<FeedbackResponse>> submitRiderRating(
            @AuthenticationPrincipal UserPrincipal rider,
            @PathVariable String rideUuid,
            @Valid @RequestBody SubmitRatingRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        FeedbackResponse response = feedbackService.submitRiderFeedback(rider, rideUuid, request, authHeader);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Driver rating submitted successfully"));
    }

    @Operation(summary = "Driver: Rate rider for a completed ride (alias route)")
    @PostMapping("/rides/{rideUuid}/rider-rating")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<FeedbackResponse>> submitDriverRatingAlias(
            @AuthenticationPrincipal UserPrincipal driver,
            @PathVariable String rideUuid,
            @Valid @RequestBody SubmitRatingRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        FeedbackResponse response = feedbackService.submitDriverFeedback(driver, rideUuid, request, authHeader);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Rider rating submitted successfully"));
    }

    @Operation(summary = "List reviews submitted by authenticated user")
    @GetMapping("/me/submitted")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<FeedbackResponse>>> getMySubmittedReviews(
            @AuthenticationPrincipal UserPrincipal user,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<FeedbackResponse> response = feedbackService.getMySubmittedFeedback(user, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "List completed trips pending feedback by authenticated user")
    @GetMapping("/me/pending")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<PendingFeedbackItemResponse>>> getMyPendingFeedback(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        List<PendingFeedbackItemResponse> response = feedbackService.getMyPendingFeedback(user, authHeader);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Get review submitted by authenticated user by UUID")
    @GetMapping("/me/reviews/{reviewUuid}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<FeedbackResponse>> getMyReviewByUuid(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String reviewUuid
    ) {
        FeedbackResponse response = feedbackService.getMyReviewByUuid(user, reviewUuid);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Update submitted review within allowed edit window")
    @PatchMapping("/me/reviews/{reviewUuid}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<FeedbackResponse>> updateMyReview(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String reviewUuid,
            @Valid @RequestBody UpdateRatingRequest request
    ) {
        FeedbackResponse response = feedbackService.updateMyReview(user, reviewUuid, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Review updated successfully"));
    }

    @Operation(summary = "Report an abusive, offensive, or inappropriate review")
    @PostMapping("/reviews/{reviewUuid}/report")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<FeedbackReportResponse>> reportReview(
            @AuthenticationPrincipal UserPrincipal reporter,
            @PathVariable String reviewUuid,
            @Valid @RequestBody ReportReviewRequest request
    ) {
        FeedbackReportResponse response = moderationService.reportReview(reporter, reviewUuid, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Report submitted successfully and queued for administrative review"));
    }

    @Operation(summary = "List standardized feedback tags catalog")
    @GetMapping("/tags")
    public ResponseEntity<ApiResponse<List<FeedbackTagDto>>> getFeedbackTags(
            @Parameter(description = "Optional filter by role (DRIVER, RIDER)")
            @RequestParam(required = false) String targetRole
    ) {
        List<FeedbackTagDto> tags = feedbackService.getFeedbackTags(targetRole);
        return ResponseEntity.ok(ApiResponse.success(tags));
    }
}
