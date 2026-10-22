package com.lexor.feedback.client;

import com.lexor.feedback.dto.client.RideEligibilityDto;

import java.util.Optional;

public interface IBookingVerificationClient {

    Optional<RideEligibilityDto> verifyRideEligibility(String rideOrBookingUuid, String authorizationHeader);

    // Optional test helper for integration testing
    void registerMockRide(RideEligibilityDto mockRide);

    void clearMockRides();
}
