package com.lexor.feedback.controller;

import com.lexor.feedback.dto.request.SubmitRatingRequest;
import com.lexor.feedback.dto.response.ApiResponse;
import com.lexor.feedback.dto.response.DriverRatingSummaryResponse;
import com.lexor.feedback.dto.response.FeedbackResponse;
import com.lexor.feedback.dto.response.PageResponse;
import com.lexor.feedback.security.UserPrincipal;
import com.lexor.feedback.service.IFeedbackService;
import com.lexor.feedback.service.IRatingCalculationService;
import io.swagger.v3.oas.annotations.Operation;
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

@RestController
@RequestMapping("/api/v1/drivers/me")
@RequiredArgsConstructor
@Tag(name = "Driver Feedback & Ratings", description = "Endpoints for assigned drivers to rate riders and view received feedback and rating aggregates")
@PreAuthorize("hasRole('DRIVER')")
public class DriverFeedbackController {

    private final IFeedbackService feedbackService;
    private final IRatingCalculationService ratingCalculationService;

    @Operation(summary = "Driver: Rate rider for a completed journey")
    @PostMapping("/rides/{rideUuid}/rider-rating")
    public ResponseEntity<ApiResponse<FeedbackResponse>> submitRiderRating(
            @AuthenticationPrincipal UserPrincipal driver,
            @PathVariable String rideUuid,
            @Valid @RequestBody SubmitRatingRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        FeedbackResponse response = feedbackService.submitDriverFeedback(driver, rideUuid, request, authHeader);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Rider rating submitted successfully"));
    }

    @Operation(summary = "Driver: List reviews received by the authenticated driver")
    @GetMapping("/feedback")
    public ResponseEntity<ApiResponse<PageResponse<FeedbackResponse>>> getReceivedFeedback(
            @AuthenticationPrincipal UserPrincipal driver,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<FeedbackResponse> response = feedbackService.getDriverReceivedFeedback(driver, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Driver: View personal comprehensive rating summary and breakdown")
    @GetMapping("/rating-summary")
    public ResponseEntity<ApiResponse<DriverRatingSummaryResponse>> getMyRatingSummary(
            @AuthenticationPrincipal UserPrincipal driver
    ) {
        DriverRatingSummaryResponse summary = ratingCalculationService.getDriverSummary(driver.getUserId());
        return ResponseEntity.ok(ApiResponse.success(summary));
    }
}
