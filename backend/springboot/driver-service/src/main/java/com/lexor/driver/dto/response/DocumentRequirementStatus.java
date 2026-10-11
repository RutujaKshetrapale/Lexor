package com.lexor.driver.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lexor.driver.entity.DocumentType;
import com.lexor.driver.entity.DocumentVerificationStatus;
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
public class DocumentRequirementStatus {

    private DocumentType documentType;
    private boolean required;
    private DocumentVerificationStatus status;
    private Long documentId;
    private String documentNumber;
    private String rejectionReason;
    private LocalDateTime uploadedAt;
    private LocalDateTime verifiedAt;
}
