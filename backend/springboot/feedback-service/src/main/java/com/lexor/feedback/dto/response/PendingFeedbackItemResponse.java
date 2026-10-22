package com.lexor.feedback.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PendingFeedbackItemResponse {

    private String rideUuid;
    private String bookingUuid;
    private String targetUuid;
    private String targetName;
    private String targetRole; // DRIVER, RIDER
    private LocalDateTime completedAt;
    private LocalDateTime submissionDeadline;
}
