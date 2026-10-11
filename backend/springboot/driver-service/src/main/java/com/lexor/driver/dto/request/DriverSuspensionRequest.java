package com.lexor.driver.dto.request;

import jakarta.validation.constraints.NotBlank;
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
public class DriverSuspensionRequest {

    @NotNull(message = "Suspension flag is required")
    private Boolean suspended;

    @NotBlank(message = "Reason is required for suspension decision")
    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;
}
