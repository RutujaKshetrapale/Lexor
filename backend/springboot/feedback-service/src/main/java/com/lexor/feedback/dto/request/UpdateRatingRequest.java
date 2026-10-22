package com.lexor.feedback.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@Schema(description = "Request body for updating an existing rating within the allowed edit window")
public class UpdateRatingRequest {

    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    @Schema(description = "Updated star rating from 1 to 5", example = "4")
    private Integer rating;

    @Size(max = 1000, message = "Review text cannot exceed 1000 characters")
    @Schema(description = "Updated written feedback", example = "Updating feedback: ride was slightly delayed due to traffic.")
    private String reviewText;

    @Schema(description = "Updated feedback tags", example = "[\"CLEAN_VEHICLE\"]")
    private List<String> tagCodes;
}
