package com.lexor.driver.service;

import com.lexor.driver.dto.request.DriverAvailabilityRequest;
import com.lexor.driver.dto.request.PatchDriverProfileRequest;
import com.lexor.driver.dto.request.UpdateDriverProfileRequest;
import com.lexor.driver.dto.response.DriverAvailabilityResponse;
import com.lexor.driver.dto.response.DriverOnboardingResponse;
import com.lexor.driver.dto.response.DriverProfileResponse;
import com.lexor.driver.entity.DriverProfile;
import com.lexor.driver.entity.User;
import com.lexor.driver.security.UserPrincipal;

public interface DriverProfileService {

    DriverProfileResponse getDriverProfile(UserPrincipal currentUser);

    DriverProfileResponse updateDriverProfile(UserPrincipal currentUser, UpdateDriverProfileRequest request);

    DriverProfileResponse patchDriverProfile(UserPrincipal currentUser, PatchDriverProfileRequest request);

    DriverOnboardingResponse getOnboardingStatus(UserPrincipal currentUser);

    DriverOnboardingResponse submitForReview(UserPrincipal currentUser);

    DriverAvailabilityResponse updateAvailability(UserPrincipal currentUser, DriverAvailabilityRequest request);

    DriverProfile getOrCreateDriverProfile(User user);

    DriverProfileResponse mapToResponse(DriverProfile driver);
}
