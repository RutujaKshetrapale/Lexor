package com.lexor.matching.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request body for retrying a matching attempt")
public class MatchingRetryRequest {

    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    @Schema(description = "Optional reason for initiating a matching retry", example = "Previous matching timed out or returned no candidates")
    private String reason;

    @Schema(description = "Flag to force re-evaluation of previous candidates", example = "false")
    @Builder.Default
    private boolean forceCandidateReevaluation = false;
}
