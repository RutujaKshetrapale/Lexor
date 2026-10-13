package com.lexor.vehicle.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lexor.vehicle.entity.DocumentVerificationStatus;
import com.lexor.vehicle.entity.VehicleDocumentType;
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
public class VehicleDocumentResponse {
    private Long id;
    private String vehicleUuid;
    private VehicleDocumentType documentType;
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
    private String reviewedByAdminName;
    private LocalDateTime reviewedAt;
    private Integer version;
    private boolean isCurrent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
