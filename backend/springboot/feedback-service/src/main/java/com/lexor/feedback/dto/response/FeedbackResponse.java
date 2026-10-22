package com.lexor.feedback.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lexor.feedback.entity.RideFeedback;
import com.lexor.feedback.enums.FeedbackRole;
import com.lexor.feedback.enums.ModerationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeedbackResponse {

    private String feedbackUuid;
    private String rideUuid;
    private String bookingUuid;
    private String reviewerUuid;
    private String targetUuid;
    private FeedbackRole reviewerRole;
    private FeedbackRole targetRole;
    private Integer rating;
    private String reviewText;
    private List<FeedbackTagDto> tags;
    private ModerationStatus moderationStatus;
    private Boolean isEdited;
    private LocalDateTime editableUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static FeedbackResponse fromEntity(RideFeedback feedback) {
        if (feedback == null) return null;

        List<FeedbackTagDto> tagDtos = feedback.getTags() != null
                ? feedback.getTags().stream().map(FeedbackTagDto::fromEntity).collect(Collectors.toList())
                : Collections.emptyList();

        return FeedbackResponse.builder()
                .feedbackUuid(feedback.getFeedbackUuid())
                .rideUuid(feedback.getRideUuid())
                .bookingUuid(feedback.getBookingUuid())
                .reviewerUuid(feedback.getReviewerUuid())
                .targetUuid(feedback.getTargetUuid())
                .reviewerRole(feedback.getReviewerRole())
                .targetRole(feedback.getTargetRole())
                .rating(feedback.getRating())
                .reviewText(feedback.getReviewText())
                .tags(tagDtos)
                .moderationStatus(feedback.getModerationStatus())
                .isEdited(feedback.getIsEdited())
                .editableUntil(feedback.getEditableUntil())
                .createdAt(feedback.getCreatedAt())
                .updatedAt(feedback.getUpdatedAt())
                .build();
    }
}
