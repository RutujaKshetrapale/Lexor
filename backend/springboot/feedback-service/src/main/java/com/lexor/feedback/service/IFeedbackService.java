package com.lexor.feedback.service;

import com.lexor.feedback.dto.request.SubmitRatingRequest;
import com.lexor.feedback.dto.request.UpdateRatingRequest;
import com.lexor.feedback.dto.response.FeedbackResponse;
import com.lexor.feedback.dto.response.FeedbackTagDto;
import com.lexor.feedback.dto.response.PageResponse;
import com.lexor.feedback.dto.response.PendingFeedbackItemResponse;
import com.lexor.feedback.enums.ModerationStatus;
import com.lexor.feedback.security.UserPrincipal;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface IFeedbackService {

    FeedbackResponse submitRiderFeedback(UserPrincipal rider, String rideUuid, SubmitRatingRequest request, String authHeader);

    FeedbackResponse submitDriverFeedback(UserPrincipal driver, String rideUuid, SubmitRatingRequest request, String authHeader);

    PageResponse<FeedbackResponse> getMySubmittedFeedback(UserPrincipal user, Pageable pageable);

    List<PendingFeedbackItemResponse> getMyPendingFeedback(UserPrincipal user, String authHeader);

    FeedbackResponse getMyReviewByUuid(UserPrincipal user, String reviewUuid);

    FeedbackResponse updateMyReview(UserPrincipal user, String reviewUuid, UpdateRatingRequest request);

    PageResponse<FeedbackResponse> getDriverReceivedFeedback(UserPrincipal driver, Pageable pageable);

    PageResponse<FeedbackResponse> getAdminFeedback(String reviewerUuid, String targetUuid, ModerationStatus status, Pageable pageable);

    FeedbackResponse getAdminFeedbackByUuid(String reviewUuid);

    List<FeedbackTagDto> getFeedbackTags(String targetRole);
}
