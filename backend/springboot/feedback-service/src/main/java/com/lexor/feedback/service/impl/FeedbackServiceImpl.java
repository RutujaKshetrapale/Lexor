package com.lexor.feedback.service.impl;

import com.lexor.feedback.client.IBookingVerificationClient;
import com.lexor.feedback.client.INotificationClient;
import com.lexor.feedback.config.FeedbackProperties;
import com.lexor.feedback.dto.client.RideEligibilityDto;
import com.lexor.feedback.dto.request.DirectNotificationRequest;
import com.lexor.feedback.dto.request.SubmitRatingRequest;
import com.lexor.feedback.dto.request.UpdateRatingRequest;
import com.lexor.feedback.dto.response.FeedbackResponse;
import com.lexor.feedback.dto.response.FeedbackTagDto;
import com.lexor.feedback.dto.response.PageResponse;
import com.lexor.feedback.dto.response.PendingFeedbackItemResponse;
import com.lexor.feedback.entity.FeedbackTag;
import com.lexor.feedback.entity.RideFeedback;
import com.lexor.feedback.enums.FeedbackRole;
import com.lexor.feedback.enums.ModerationStatus;
import com.lexor.feedback.enums.TagTargetRole;
import com.lexor.feedback.exception.*;
import com.lexor.feedback.repository.FeedbackTagRepository;
import com.lexor.feedback.repository.RideFeedbackRepository;
import com.lexor.feedback.security.UserPrincipal;
import com.lexor.feedback.service.IFeedbackService;
import com.lexor.feedback.service.IRatingCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackServiceImpl implements IFeedbackService {

    private final RideFeedbackRepository feedbackRepository;
    private final FeedbackTagRepository tagRepository;
    private final IBookingVerificationClient bookingClient;
    private final INotificationClient notificationClient;
    private final IRatingCalculationService ratingCalculationService;
    private final FeedbackProperties properties;

    @Override
    @Transactional
    public FeedbackResponse submitRiderFeedback(UserPrincipal rider, String rideUuid, SubmitRatingRequest request, String authHeader) {
        log.info("Rider {} submitting feedback for ride {}", rider.getUserId(), rideUuid);

        // 1. Authoritative ride verification
        RideEligibilityDto ride = bookingClient.verifyRideEligibility(rideUuid, authHeader)
                .orElseThrow(() -> new ResourceNotFoundException("Ride not found with UUID: " + rideUuid));

        // 2. Validate authoritative completed state
        if (!ride.isAuthoritativeCompleted()) {
            throw new IneligibleFeedbackException("Cannot review ride in status: " + ride.getStatus() + ". Ride must be completed.");
        }

        // 3. Validate reviewer participation
        if (ride.getRiderUuid() == null || !ride.getRiderUuid().equalsIgnoreCase(rider.getUserId())) {
            throw new ForbiddenException("Access denied: You are not the rider on this ride");
        }

        // 4. Validate driver existence on ride
        if (ride.getDriverUuid() == null || ride.getDriverUuid().isBlank()) {
            throw new IneligibleFeedbackException("Ride does not have an assigned driver to evaluate");
        }

        // 5. Check submission deadline
        LocalDateTime completedAt = ride.getCompletedAt() != null ? ride.getCompletedAt() : LocalDateTime.now();
        int deadlineDays = properties.getPolicy().getSubmissionWindowDays();
        if (completedAt.plusDays(deadlineDays).isBefore(LocalDateTime.now())) {
            throw new IneligibleFeedbackException("Feedback submission deadline has expired (" + deadlineDays + " days)");
        }

        // 6. Check duplicate submission
        if (feedbackRepository.existsByRideUuidAndReviewerUuidAndReviewerRole(ride.getRideUuid(), rider.getUserId(), FeedbackRole.RIDER)) {
            throw new ConflictException("You have already submitted a review for this ride");
        }

        // 7. Resolve tags
        Set<FeedbackTag> tags = resolveTags(request.getTagCodes(), TagTargetRole.DRIVER);

        // 8. Create feedback record
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime editableUntil = properties.getPolicy().isEditEnabled()
                ? now.plusMinutes(properties.getPolicy().getEditWindowMinutes())
                : null;

        RideFeedback feedback = RideFeedback.builder()
                .feedbackUuid(UUID.randomUUID().toString())
                .rideUuid(ride.getRideUuid())
                .bookingUuid(ride.getBookingUuid())
                .reviewerUuid(rider.getUserId())
                .targetUuid(ride.getDriverUuid())
                .reviewerRole(FeedbackRole.RIDER)
                .targetRole(FeedbackRole.DRIVER)
                .rating(request.getRating())
                .reviewText(request.getReviewText())
                .moderationStatus(ModerationStatus.VISIBLE)
                .isEdited(false)
                .editableUntil(editableUntil)
                .tags(tags)
                .build();

        RideFeedback saved = feedbackRepository.save(feedback);

        // 9. Update driver rating summary
        ratingCalculationService.updateRatingSummaryOnNewFeedback(saved);

        // 10. Asynchronous notification dispatch
        notificationClient.sendNotification(DirectNotificationRequest.builder()
                .recipientUuid(ride.getDriverUuid())
                .recipientRole("DRIVER")
                .notificationType("NEW_RATING_RECEIVED")
                .category("RIDE")
                .title("New Rider Rating")
                .body("You received a " + request.getRating() + "-star rating for your recent trip.")
                .relatedEntityType("RIDE")
                .relatedEntityUuid(ride.getRideUuid())
                .idempotencyKey("NOTIF-RATING-" + saved.getFeedbackUuid())
                .channels(List.of("PUSH", "IN_APP"))
                .build());

        return FeedbackResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public FeedbackResponse submitDriverFeedback(UserPrincipal driver, String rideUuid, SubmitRatingRequest request, String authHeader) {
        log.info("Driver {} submitting feedback for ride {}", driver.getUserId(), rideUuid);

        // 1. Authoritative ride verification
        RideEligibilityDto ride = bookingClient.verifyRideEligibility(rideUuid, authHeader)
                .orElseThrow(() -> new ResourceNotFoundException("Ride not found with UUID: " + rideUuid));

        // 2. Validate authoritative completed state
        if (!ride.isAuthoritativeCompleted()) {
            throw new IneligibleFeedbackException("Cannot review ride in status: " + ride.getStatus() + ". Ride must be completed.");
        }

        // 3. Validate driver participation
        if (ride.getDriverUuid() == null || !ride.getDriverUuid().equalsIgnoreCase(driver.getUserId())) {
            throw new ForbiddenException("Access denied: You are not the assigned driver for this ride");
        }

        // 4. Validate rider existence on ride
        if (ride.getRiderUuid() == null || ride.getRiderUuid().isBlank()) {
            throw new IneligibleFeedbackException("Ride does not have a valid rider reference");
        }

        // 5. Check submission deadline
        LocalDateTime completedAt = ride.getCompletedAt() != null ? ride.getCompletedAt() : LocalDateTime.now();
        int deadlineDays = properties.getPolicy().getSubmissionWindowDays();
        if (completedAt.plusDays(deadlineDays).isBefore(LocalDateTime.now())) {
            throw new IneligibleFeedbackException("Feedback submission deadline has expired (" + deadlineDays + " days)");
        }

        // 6. Check duplicate submission
        if (feedbackRepository.existsByRideUuidAndReviewerUuidAndReviewerRole(ride.getRideUuid(), driver.getUserId(), FeedbackRole.DRIVER)) {
            throw new ConflictException("You have already submitted a review for this ride");
        }

        // 7. Resolve tags
        Set<FeedbackTag> tags = resolveTags(request.getTagCodes(), TagTargetRole.RIDER);

        // 8. Create feedback record
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime editableUntil = properties.getPolicy().isEditEnabled()
                ? now.plusMinutes(properties.getPolicy().getEditWindowMinutes())
                : null;

        RideFeedback feedback = RideFeedback.builder()
                .feedbackUuid(UUID.randomUUID().toString())
                .rideUuid(ride.getRideUuid())
                .bookingUuid(ride.getBookingUuid())
                .reviewerUuid(driver.getUserId())
                .targetUuid(ride.getRiderUuid())
                .reviewerRole(FeedbackRole.DRIVER)
                .targetRole(FeedbackRole.RIDER)
                .rating(request.getRating())
                .reviewText(request.getReviewText())
                .moderationStatus(ModerationStatus.VISIBLE)
                .isEdited(false)
                .editableUntil(editableUntil)
                .tags(tags)
                .build();

        RideFeedback saved = feedbackRepository.save(feedback);

        // 9. Update rider rating summary
        ratingCalculationService.updateRatingSummaryOnNewFeedback(saved);

        return FeedbackResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FeedbackResponse> getMySubmittedFeedback(UserPrincipal user, Pageable pageable) {
        Page<RideFeedback> page = feedbackRepository.findByReviewerUuidOrderByCreatedAtDesc(user.getUserId(), pageable);
        List<FeedbackResponse> dtos = page.getContent().stream()
                .map(FeedbackResponse::fromEntity)
                .collect(Collectors.toList());

        return PageResponse.<FeedbackResponse>builder()
                .content(dtos)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingFeedbackItemResponse> getMyPendingFeedback(UserPrincipal user, String authHeader) {
        // Find recent rides pending feedback
        return Collections.emptyList();
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackResponse getMyReviewByUuid(UserPrincipal user, String reviewUuid) {
        RideFeedback feedback = feedbackRepository.findByFeedbackUuid(reviewUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with UUID: " + reviewUuid));

        if (!feedback.getReviewerUuid().equalsIgnoreCase(user.getUserId())) {
            throw new ForbiddenException("Access denied: You do not own this review");
        }

        return FeedbackResponse.fromEntity(feedback);
    }

    @Override
    @Transactional
    public FeedbackResponse updateMyReview(UserPrincipal user, String reviewUuid, UpdateRatingRequest request) {
        RideFeedback feedback = feedbackRepository.findByFeedbackUuid(reviewUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with UUID: " + reviewUuid));

        if (!feedback.getReviewerUuid().equalsIgnoreCase(user.getUserId())) {
            throw new ForbiddenException("Access denied: You do not own this review");
        }

        if (!properties.getPolicy().isEditEnabled()) {
            throw new BadRequestException("Review editing is currently disabled");
        }

        if (feedback.getEditableUntil() != null && LocalDateTime.now().isAfter(feedback.getEditableUntil())) {
            throw new BadRequestException("The edit window for this review has expired");
        }

        int oldRating = feedback.getRating();
        boolean ratingChanged = false;

        if (request.getRating() != null && !request.getRating().equals(oldRating)) {
            feedback.setRating(request.getRating());
            ratingChanged = true;
        }

        if (request.getReviewText() != null) {
            feedback.setReviewText(request.getReviewText());
        }

        if (request.getTagCodes() != null) {
            TagTargetRole allowedRole = feedback.getReviewerRole() == FeedbackRole.RIDER ? TagTargetRole.DRIVER : TagTargetRole.RIDER;
            feedback.setTags(resolveTags(request.getTagCodes(), allowedRole));
        }

        feedback.setIsEdited(true);
        RideFeedback updated = feedbackRepository.save(feedback);

        if (ratingChanged) {
            ratingCalculationService.updateRatingSummaryOnRatingEdit(updated, oldRating);
        }

        return FeedbackResponse.fromEntity(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FeedbackResponse> getDriverReceivedFeedback(UserPrincipal driver, Pageable pageable) {
        Page<RideFeedback> page = feedbackRepository.findByTargetUuidAndModerationStatusOrderByCreatedAtDesc(
                driver.getUserId(), ModerationStatus.VISIBLE, pageable);

        List<FeedbackResponse> dtos = page.getContent().stream()
                .map(FeedbackResponse::fromEntity)
                .collect(Collectors.toList());

        return PageResponse.<FeedbackResponse>builder()
                .content(dtos)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FeedbackResponse> getAdminFeedback(
            String reviewerUuid, String targetUuid, ModerationStatus status, Pageable pageable) {

        Specification<RideFeedback> spec = Specification.where(null);

        if (reviewerUuid != null && !reviewerUuid.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("reviewerUuid"), reviewerUuid));
        }
        if (targetUuid != null && !targetUuid.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("targetUuid"), targetUuid));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("moderationStatus"), status));
        }

        Page<RideFeedback> page = feedbackRepository.findAll(spec, pageable);
        List<FeedbackResponse> dtos = page.getContent().stream()
                .map(FeedbackResponse::fromEntity)
                .collect(Collectors.toList());

        return PageResponse.<FeedbackResponse>builder()
                .content(dtos)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackResponse getAdminFeedbackByUuid(String reviewUuid) {
        RideFeedback feedback = feedbackRepository.findByFeedbackUuid(reviewUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with UUID: " + reviewUuid));
        return FeedbackResponse.fromEntity(feedback);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackTagDto> getFeedbackTags(String targetRole) {
        List<FeedbackTag> tags;
        if (targetRole != null && !targetRole.isBlank()) {
            try {
                TagTargetRole role = TagTargetRole.valueOf(targetRole.toUpperCase());
                tags = tagRepository.findByTargetRoleInAndIsActiveTrueOrderByDisplayOrderAsc(List.of(role, TagTargetRole.BOTH));
            } catch (IllegalArgumentException e) {
                tags = tagRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
            }
        } else {
            tags = tagRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        }

        return tags.stream().map(FeedbackTagDto::fromEntity).collect(Collectors.toList());
    }

    private Set<FeedbackTag> resolveTags(List<String> tagCodes, TagTargetRole targetRole) {
        if (tagCodes == null || tagCodes.isEmpty()) {
            return new HashSet<>();
        }

        List<FeedbackTag> matched = tagRepository.findByTagCodeIn(tagCodes);
        Set<FeedbackTag> filtered = new HashSet<>();
        for (FeedbackTag tag : matched) {
            if (tag.getIsActive() && (tag.getTargetRole() == targetRole || tag.getTargetRole() == TagTargetRole.BOTH)) {
                filtered.add(tag);
            }
        }
        return filtered;
    }
}
