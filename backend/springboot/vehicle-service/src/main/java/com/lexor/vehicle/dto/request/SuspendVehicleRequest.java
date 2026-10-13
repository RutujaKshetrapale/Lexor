package com.lexor.vehicle.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to suspend or reinstate a vehicle")
public class SuspendVehicleRequest {

    @NotBlank(message = "Action is required ('SUSPEND' or 'REINSTATE')")
    @Schema(description = "Action: SUSPEND or REINSTATE", example = "SUSPEND")
    private String action;

    @NotBlank(message = "Reason is required")
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    @Schema(description = "Detailed justification for the administrative suspension or reinstatement", example = "Reported mechanical safety issues during vehicle inspection")
    private String reason;
}
