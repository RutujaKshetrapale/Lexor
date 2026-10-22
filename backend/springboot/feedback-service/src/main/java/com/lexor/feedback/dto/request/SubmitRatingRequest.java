package com.lexor.feedback.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request body for submitting rating and feedback for a completed ride")
public class SubmitRatingRequest {

    @NotNull(message = "Rating value is required")
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    @Schema(description = "Star rating from 1 to 5", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer rating;

    @Size(max = 1000, message = "Review text cannot exceed 1000 characters")
    @Schema(description = "Optional written feedback or review comments", example = "Great driver, vehicle was spotless and driving was very safe.")
    private String reviewText;

    @Schema(description = "List of structured feedback tag codes", example = "[\"SAFE_DRIVING\", \"CLEAN_VEHICLE\", \"SMOOTH_RIDE\"]")
    private List<String> tagCodes;
}
