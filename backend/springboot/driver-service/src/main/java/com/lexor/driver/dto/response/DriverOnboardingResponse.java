package com.lexor.driver.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lexor.driver.entity.OnboardingStatus;
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
public class DriverOnboardingResponse {

    private String driverUuid;
    private OnboardingStatus onboardingStatus;
    private int completionPercentage;
    private boolean profileComplete;
    private boolean documentsComplete;
    private boolean canSubmitForReview;
    private boolean isEligibleForOnline;
    private List<DocumentRequirementStatus> documentChecklist;
    private List<String> outstandingTasks;
    private String rejectionReason;
}
