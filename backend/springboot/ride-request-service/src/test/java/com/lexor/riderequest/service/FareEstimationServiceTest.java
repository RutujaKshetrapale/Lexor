package com.lexor.riderequest.service;

import com.lexor.riderequest.dto.response.FareEstimateResponse;
import com.lexor.riderequest.entity.RideType;
import com.lexor.riderequest.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class FareEstimationServiceTest {

    private FareEstimationService fareEstimationService;
    private RideType goRideType;
    private RideType premierRideType;

    // Bangalore Coordinates
    // MG Road: 12.9784, 77.6408
    // Prestige Tech Park Marathahalli: 12.9372, 77.6949
    private final BigDecimal pickupLat = new BigDecimal("12.97840000");
    private final BigDecimal pickupLng = new BigDecimal("77.64080000");
    private final BigDecimal dropLat = new BigDecimal("12.93720000");
    private final BigDecimal dropLng = new BigDecimal("77.69490000");

    @BeforeEach
    void setUp() {
        fareEstimationService = new FareEstimationService();

        goRideType = RideType.builder()
                .id(1L)
                .name("LEXOR Go")
                .code("GO")
                .baseFare(new BigDecimal("50.00"))
                .perKmRate(new BigDecimal("15.00"))
                .perMinuteRate(new BigDecimal("2.50"))
                .minimumFare(new BigDecimal("80.00"))
                .cancellationFee(new BigDecimal("30.00"))
                .capacity(4)
                .isActive(true)
                .build();

        premierRideType = RideType.builder()
                .id(3L)
                .name("LEXOR Premier")
                .code("PREMIER")
                .baseFare(new BigDecimal("120.00"))
                .perKmRate(new BigDecimal("28.00"))
                .perMinuteRate(new BigDecimal("4.50"))
                .minimumFare(new BigDecimal("180.00"))
                .cancellationFee(new BigDecimal("70.00"))
                .capacity(4)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Calculate route distance using Haversine formula with urban curvature factor")
    void testCalculateDistanceKm() {
        BigDecimal distance = fareEstimationService.calculateDistanceKm(pickupLat, pickupLng, dropLat, dropLng);

        assertNotNull(distance);
        // Straight line ~7.5 km, with 1.30x urban routing factor ~9.75 km
        assertTrue(distance.compareTo(new BigDecimal("8.00")) > 0);
        assertTrue(distance.compareTo(new BigDecimal("12.00")) < 0);
    }

    @Test
    @DisplayName("Calculate estimated duration in minutes based on distance")
    void testCalculateDurationMinutes() {
        BigDecimal distance = new BigDecimal("10.00"); // 10 km
        Integer durationMinutes = fareEstimationService.calculateDurationMinutes(distance);

        assertNotNull(durationMinutes);
        // At 25 km/h: 10 / 25 * 60 = 24 minutes
        assertEquals(24, durationMinutes);
    }

    @Test
    @DisplayName("Enforce minimum duration of 5 minutes for short distances")
    void testCalculateDurationMinimumThreshold() {
        BigDecimal shortDistance = new BigDecimal("0.50"); // 500 meters
        Integer durationMinutes = fareEstimationService.calculateDurationMinutes(shortDistance);

        assertEquals(5, durationMinutes);
    }

    @Test
    @DisplayName("Reject identical pickup and drop coordinates")
    void testRejectIdenticalCoordinates() {
        assertThrows(BadRequestException.class, () ->
                fareEstimationService.calculateDistanceKm(pickupLat, pickupLng, pickupLat, pickupLng)
        );
    }

    @Test
    @DisplayName("Reject null coordinates")
    void testRejectNullCoordinates() {
        assertThrows(BadRequestException.class, () ->
                fareEstimationService.calculateDistanceKm(null, pickupLng, dropLat, dropLng)
        );
    }

    @Test
    @DisplayName("Calculate fare breakdown with subtotal above minimum fare")
    void testEstimateFareAboveMinimum() {
        BigDecimal distance = new BigDecimal("10.00"); // 10 km
        int duration = 24; // 24 mins

        // GO rates: Base = 50.00, perKm = 15.00, perMin = 2.50, min = 80.00
        // distanceFare = 10 * 15 = 150.00
        // timeFare = 24 * 2.50 = 60.00
        // subtotal = 50 + 150 + 60 = 260.00
        // finalFare = max(260.00, 80.00) = 260.00
        FareEstimateResponse estimate = fareEstimationService.estimateFare(distance, duration, goRideType);

        assertNotNull(estimate);
        assertEquals(new BigDecimal("50.00"), estimate.getBaseFare());
        assertEquals(new BigDecimal("150.00"), estimate.getDistanceFare());
        assertEquals(new BigDecimal("60.00"), estimate.getTimeFare());
        assertEquals(new BigDecimal("260.00"), estimate.getSubtotalFare());
        assertEquals(new BigDecimal("80.00"), estimate.getMinimumFare());
        assertEquals(new BigDecimal("260.00"), estimate.getFinalEstimatedFare());
        assertEquals("INR", estimate.getCurrency());
    }

    @Test
    @DisplayName("Enforce minimum fare threshold when computed fare is lower")
    void testEstimateFareBelowMinimumEnforcesMinimumFare() {
        BigDecimal shortDistance = new BigDecimal("0.50"); // 0.5 km
        int duration = 5; // 5 mins

        // GO rates: Base = 50.00, perKm = 15.00, perMin = 2.50, min = 80.00
        // distanceFare = 0.5 * 15 = 7.50
        // timeFare = 5 * 2.50 = 12.50
        // subtotal = 50 + 7.50 + 12.50 = 70.00
        // finalFare = max(70.00, 80.00) = 80.00 (minimum fare enforced!)
        FareEstimateResponse estimate = fareEstimationService.estimateFare(shortDistance, duration, goRideType);

        assertNotNull(estimate);
        assertEquals(new BigDecimal("70.00"), estimate.getSubtotalFare());
        assertEquals(new BigDecimal("80.00"), estimate.getMinimumFare());
        assertEquals(new BigDecimal("80.00"), estimate.getFinalEstimatedFare());
    }

    @Test
    @DisplayName("Calculate Premier category fare accurately")
    void testEstimateFarePremierCategory() {
        BigDecimal distance = new BigDecimal("10.00");
        int duration = 24;

        // Premier: Base = 120.00, perKm = 28.00, perMin = 4.50, min = 180.00
        // distanceFare = 10 * 28 = 280.00
        // timeFare = 24 * 4.50 = 108.00
        // subtotal = 120 + 280 + 108 = 508.00
        FareEstimateResponse estimate = fareEstimationService.estimateFare(distance, duration, premierRideType);

        assertNotNull(estimate);
        assertEquals(new BigDecimal("120.00"), estimate.getBaseFare());
        assertEquals(new BigDecimal("280.00"), estimate.getDistanceFare());
        assertEquals(new BigDecimal("108.00"), estimate.getTimeFare());
        assertEquals(new BigDecimal("508.00"), estimate.getFinalEstimatedFare());
    }
}
