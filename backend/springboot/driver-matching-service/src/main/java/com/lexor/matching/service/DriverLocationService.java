package com.lexor.matching.service;

import com.lexor.matching.dto.request.LocationUpdateRequest;
import com.lexor.matching.dto.response.LocationUpdateResponse;
import com.lexor.matching.entity.DriverLocation;
import com.lexor.matching.security.UserPrincipal;

import java.util.Optional;

public interface DriverLocationService {

    LocationUpdateResponse updateDriverLocation(UserPrincipal driverPrincipal, LocationUpdateRequest request);

    Optional<DriverLocation> getLatestLocation(Long driverId);

    boolean isLocationFresh(DriverLocation location, long maxAgeSeconds);
}
