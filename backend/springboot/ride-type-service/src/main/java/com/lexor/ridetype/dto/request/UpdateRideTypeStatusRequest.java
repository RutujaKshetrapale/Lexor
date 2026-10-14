package com.lexor.ridetype.dto.request;

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
@Schema(description = "Request payload for toggling ride type active status")
public class UpdateRideTypeStatusRequest {

    @NotNull(message = "isActive status is required")
    @Schema(description = "Target status (true for enabled/active, false for disabled)", example = "false")
    private Boolean isActive;

    @Size(max = 255, message = "Reason must not exceed 255 characters")
    @Schema(description = "Administrative reason for enabling/disabling the category", example = "Temporary fleet maintenance")
    private String reason;
}
