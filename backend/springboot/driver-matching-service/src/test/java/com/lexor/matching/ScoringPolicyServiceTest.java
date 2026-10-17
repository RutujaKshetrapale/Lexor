package com.lexor.matching;

import com.lexor.matching.entity.DriverProfile;
import com.lexor.matching.service.ScoringPolicyService;
import com.lexor.matching.service.impl.ScoringPolicyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoringPolicyServiceTest {

    private ScoringPolicyService scoringPolicyService;

    @BeforeEach
    void setUp() {
        scoringPolicyService = new ScoringPolicyServiceImpl();
    }

    @Test
    @DisplayName("Calculate accurate Haversine distance between Bengaluru landmarks")
    void testCalculateHaversineDistance() {
        // MG Road to Prestige Tech Park Marathahalli (~11-12 km)
        double lat1 = 12.9784;
        double lon1 = 77.6408;
        double lat2 = 12.9372;
        double lon2 = 77.6949;

        double distance = scoringPolicyService.calculateHaversineDistanceKm(lat1, lon1, lat2, lon2);

        assertTrue(distance > 7.0 && distance < 12.0, "Distance should be approx 7-12 km, got: " + distance);
    }

    @Test
    @DisplayName("Zero distance when pickup equals driver location")
    void testZeroDistance() {
        double lat = 12.9784;
        double lon = 77.6408;

        double distance = scoringPolicyService.calculateHaversineDistanceKm(lat, lon, lat, lon);

        assertEquals(0.0, distance);
    }

    @Test
    @DisplayName("Calculate deterministic match score with distance, rating, and acceptance rate")
    void testCalculateMatchScore() {
        DriverProfile driver = DriverProfile.builder()
                .rating(new BigDecimal("4.90"))
                .acceptanceRate(new BigDecimal("98.50"))
                .build();

        // 2 km out of 10 km max radius
        BigDecimal score = scoringPolicyService.calculateMatchScore(driver, 2.0, 10.0);

        // Distance: 50 * (1 - 0.2) = 40.0
        // Rating: (4.90 / 5.0) * 30 = 29.4
        // Acceptance: (98.50 / 100.0) * 20 = 19.7
        // Total expected: ~89.10
        assertTrue(score.doubleValue() > 88.0 && score.doubleValue() < 90.0, "Score should be ~89.10, got: " + score);
    }

    @Test
    @DisplayName("Closer driver receives higher score than farther driver with identical ratings")
    void testCloserDriverReceivesHigherScore() {
        DriverProfile driver = DriverProfile.builder()
                .rating(new BigDecimal("4.80"))
                .acceptanceRate(new BigDecimal("95.00"))
                .build();

        BigDecimal closeScore = scoringPolicyService.calculateMatchScore(driver, 1.0, 10.0);
        BigDecimal farScore = scoringPolicyService.calculateMatchScore(driver, 8.0, 10.0);

        assertTrue(closeScore.compareTo(farScore) > 0, "Closer driver should have higher score");
    }
}
