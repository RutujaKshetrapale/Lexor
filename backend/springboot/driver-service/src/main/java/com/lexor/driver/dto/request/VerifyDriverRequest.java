package com.lexor.driver.dto.request;

import com.lexor.driver.entity.OnboardingStatus;
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
public class VerifyDriverRequest {

    @NotNull(message = "Onboarding verification status is required")
    private OnboardingStatus status;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;
}
