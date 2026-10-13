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
@Schema(description = "Request to manage driver-vehicle assignment")
public class AssignDriverRequest {

    @NotBlank(message = "Driver user UUID is required")
    @Schema(description = "UUID of the driver user to assign to this vehicle", example = "driver-uuid-888")
    private String driverUuid;

    @Size(max = 500, message = "Reason must not exceed 500 characters")
    @Schema(description = "Assignment note or reason", example = "Fleet fleet-manager reassignment")
    private String reason;
}
