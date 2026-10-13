package com.lexor.vehicle.dto.request;

import com.lexor.vehicle.entity.VehicleVerificationStatus;
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
@Schema(description = "Request by an administrator to approve or reject a vehicle")
public class ReviewVehicleRequest {

    @NotNull(message = "Decision status is required (VERIFIED or REJECTED)")
    @Schema(description = "Review decision: VERIFIED or REJECTED", example = "VERIFIED")
    private VehicleVerificationStatus status;

    @Size(max = 500, message = "Rejection reason must not exceed 500 characters")
    @Schema(description = "Reason for rejection if status is REJECTED", example = "Registration Certificate expired or blurred")
    private String rejectionReason;

    @Size(max = 500, message = "Review notes must not exceed 500 characters")
    @Schema(description = "Internal administrator notes", example = "Verified against national transport database")
    private String notes;
}
