package com.lexor.driver.service;

import com.lexor.driver.dto.response.DriverOnboardingResponse;
import com.lexor.driver.entity.DriverProfile;

public interface OnboardingPolicyService {

    DriverOnboardingResponse calculateOnboardingProgress(DriverProfile driver);

    boolean isProfileComplete(DriverProfile driver);

    boolean areAllRequiredDocumentsVerified(Long driverId);

    boolean areAllRequiredDocumentsSubmitted(Long driverId);

    void evaluateAndSyncDriverOnboardingStatus(DriverProfile driver);
}
