package com.lexor.driver.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lexor.driver.entity.DocumentType;
import com.lexor.driver.entity.DocumentVerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentResponse {

    private Long id;
    private Long driverId;
    private DocumentType documentType;
    private String documentNumber;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private String issuingAuthority;
    private DocumentVerificationStatus verificationStatus;
    private String rejectionReason;
    private String reviewNotes;
    private Long reviewedByAdminId;
    private LocalDateTime reviewedAt;
    private Integer version;
    private Boolean isCurrent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
