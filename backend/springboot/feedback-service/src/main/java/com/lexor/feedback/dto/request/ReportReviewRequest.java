package com.lexor.feedback.dto.request;

import com.lexor.feedback.enums.ReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request body for reporting an inappropriate or abusive review")
public class ReportReviewRequest {

    @NotNull(message = "Report reason is required")
    @Schema(description = "Category of violation or concern", example = "OFFENSIVE_CONTENT", requiredMode = Schema.RequiredMode.REQUIRED)
    private ReportReason reason;

    @Size(max = 500, message = "Comments cannot exceed 500 characters")
    @Schema(description = "Details describing why this review is being reported", example = "Review contains abusive language and harassment.")
    private String comments;
}
