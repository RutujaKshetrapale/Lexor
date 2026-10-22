package com.lexor.feedback.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lexor.feedback.entity.FeedbackReport;
import com.lexor.feedback.enums.ReportReason;
import com.lexor.feedback.enums.ReportStatus;
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
public class FeedbackReportResponse {

    private String reportUuid;
    private String feedbackUuid;
    private String reporterUuid;
    private ReportReason reason;
    private String comments;
    private ReportStatus status;
    private String resolutionNotes;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;

    public static FeedbackReportResponse fromEntity(FeedbackReport report) {
        if (report == null) return null;
        return FeedbackReportResponse.builder()
                .reportUuid(report.getReportUuid())
                .feedbackUuid(report.getFeedback() != null ? report.getFeedback().getFeedbackUuid() : null)
                .reporterUuid(report.getReporterUuid())
                .reason(report.getReason())
                .comments(report.getComments())
                .status(report.getStatus())
                .resolutionNotes(report.getResolutionNotes())
                .resolvedBy(report.getResolvedBy())
                .resolvedAt(report.getResolvedAt())
                .createdAt(report.getCreatedAt())
                .build();
    }
}
