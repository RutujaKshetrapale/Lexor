package com.lexor.vehicle.dto.request;

import com.lexor.vehicle.entity.DocumentVerificationStatus;
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
@Schema(description = "Request to review an individual vehicle compliance document")
public class ReviewVehicleDocumentRequest {

    @NotNull(message = "Document verification status is required")
    @Schema(description = "Review decision: VERIFIED, REJECTED, or RESUBMISSION_REQUIRED", example = "VERIFIED")
    private DocumentVerificationStatus status;

    @Size(max = 500, message = "Rejection reason must not exceed 500 characters")
    @Schema(description = "Reason if rejected or resubmission requested", example = "Insurance policy document expired")
    private String rejectionReason;

    @Size(max = 500, message = "Review notes must not exceed 500 characters")
    @Schema(description = "Administrative audit notes", example = "Insurance verified with insurer portal")
    private String reviewNotes;
}
