package com.lexor.vehicle.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to toggle operational active/inactive status")
public class VehicleStatusUpdateRequest {

    @NotNull(message = "isActive flag is required")
    @Schema(description = "Operational active status", example = "true")
    private Boolean isActive;
}
