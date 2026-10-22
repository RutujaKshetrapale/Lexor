package com.lexor.feedback.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lexor.feedback.entity.FeedbackModerationHistory;
import com.lexor.feedback.enums.ModerationStatus;
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
public class ModerationHistoryResponse {

    private String moderatorUuid;
    private ModerationStatus previousStatus;
    private ModerationStatus newStatus;
    private String reason;
    private LocalDateTime moderatedAt;

    public static ModerationHistoryResponse fromEntity(FeedbackModerationHistory history) {
        if (history == null) return null;
        return ModerationHistoryResponse.builder()
                .moderatorUuid(history.getModeratorUuid())
                .previousStatus(history.getPreviousStatus())
                .newStatus(history.getNewStatus())
                .reason(history.getReason())
                .moderatedAt(history.getModeratedAt())
                .build();
    }
}
