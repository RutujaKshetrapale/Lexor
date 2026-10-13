package com.lexor.vehicle.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lexor.vehicle.entity.VehicleDocumentType;
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
public class VehicleVerificationHistoryResponse {
    private Long id;
    private String vehicleUuid;
    private Long documentId;
    private VehicleDocumentType documentType;
    private String actionType;
    private String previousStatus;
    private String newStatus;
    private String reviewedByAdminName;
    private String reason;
    private LocalDateTime createdAt;
}
