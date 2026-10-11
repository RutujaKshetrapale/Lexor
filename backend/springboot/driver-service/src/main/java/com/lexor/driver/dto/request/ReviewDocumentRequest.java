package com.lexor.driver.dto.request;

import com.lexor.driver.entity.DocumentVerificationStatus;
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
public class ReviewDocumentRequest {

    @NotNull(message = "Document verification status is required")
    private DocumentVerificationStatus status;

    @Size(max = 500, message = "Rejection reason cannot exceed 500 characters")
    private String rejectionReason;

    @Size(max = 500, message = "Review notes cannot exceed 500 characters")
    private String reviewNotes;
}
