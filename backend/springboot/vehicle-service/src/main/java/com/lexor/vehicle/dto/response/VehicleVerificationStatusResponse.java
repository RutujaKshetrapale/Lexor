package com.lexor.vehicle.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lexor.vehicle.entity.VehicleDocumentType;
import com.lexor.vehicle.entity.VehicleVerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VehicleVerificationStatusResponse {
    private String vehicleUuid;
    private VehicleVerificationStatus status;
    private String rejectionReason;
    private boolean isEligibleForAssignment;
    private List<VehicleDocumentType> requiredDocuments;
    private List<VehicleDocumentType> uploadedDocuments;
    private List<VehicleDocumentType> missingDocuments;
    private List<VehicleDocumentType> pendingReviewDocuments;
    private List<VehicleDocumentType> rejectedDocuments;
}
