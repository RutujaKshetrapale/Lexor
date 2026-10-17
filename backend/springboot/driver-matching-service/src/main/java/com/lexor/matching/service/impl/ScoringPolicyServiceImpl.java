package com.lexor.matching.service.impl;

import com.lexor.matching.entity.DriverProfile;
import com.lexor.matching.service.ScoringPolicyService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ScoringPolicyServiceImpl implements ScoringPolicyService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    @Override
    public double calculateHaversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        double distance = EARTH_RADIUS_KM * c;
        return BigDecimal.valueOf(distance).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    @Override
    public BigDecimal calculateMatchScore(DriverProfile driver, double distanceKm, double maxRadiusKm) {
        // 1. Distance factor: Up to 50 points (closer drivers receive higher points)
        double distanceFraction = maxRadiusKm > 0 ? (distanceKm / maxRadiusKm) : 1.0;
        double distanceScore = Math.max(0.0, 50.0 * (1.0 - distanceFraction));

        // 2. Rating factor: Up to 30 points (5.0 rating = 30 points)
        double ratingVal = driver.getRating() != null ? driver.getRating().doubleValue() : 5.0;
        double ratingScore = Math.min(30.0, (ratingVal / 5.0) * 30.0);

        // 3. Acceptance rate factor: Up to 20 points (100% acceptance = 20 points)
        double acceptanceVal = driver.getAcceptanceRate() != null ? driver.getAcceptanceRate().doubleValue() : 100.0;
        double acceptanceScore = Math.min(20.0, (acceptanceVal / 100.0) * 20.0);

        double totalScore = distanceScore + ratingScore + acceptanceScore;

        return BigDecimal.valueOf(totalScore).setScale(2, RoundingMode.HALF_UP);
    }
}
