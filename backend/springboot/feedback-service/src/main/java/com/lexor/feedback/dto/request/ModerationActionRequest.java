package com.lexor.feedback.dto.request;

import com.lexor.feedback.enums.ModerationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Request body for performing an administrative moderation action on a review")
public class ModerationActionRequest {

    @NotNull(message = "New moderation status is required")
    @Schema(description = "Target moderation lifecycle status", example = "HIDDEN", requiredMode = Schema.RequiredMode.REQUIRED)
    private ModerationStatus newStatus;

    @NotBlank(message = "Moderation reason is mandatory for audit compliance")
    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    @Schema(description = "Mandatory administrative reason for this moderation change", example = "Violates community guidelines - personal attack against driver", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;
}
