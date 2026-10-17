package com.lexor.matching.service;

import com.lexor.matching.entity.DriverProfile;

import java.math.BigDecimal;

public interface ScoringPolicyService {

    double calculateHaversineDistanceKm(double lat1, double lon1, double lat2, double lon2);

    BigDecimal calculateMatchScore(DriverProfile driver, double distanceKm, double maxRadiusKm);
}
