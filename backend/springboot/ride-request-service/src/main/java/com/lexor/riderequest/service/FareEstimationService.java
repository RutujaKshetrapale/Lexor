package com.lexor.riderequest.service;

import com.lexor.riderequest.dto.response.FareEstimateResponse;
import com.lexor.riderequest.entity.RideType;
import com.lexor.riderequest.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Dedicated fare estimation and route approximation service.
 *
 * <p><strong>Development Heuristic Estimator Disclaimer:</strong>
 * In this microservices phase, a dedicated external road routing provider (e.g. Google Directions,
 * Mapbox Matrix, or OSRM) is not yet integrated. This component uses the great-circle Haversine formula
 * augmented by an empirical urban street-network tortuosity factor (1.30x) and average urban speed (25 km/h)
 * to estimate route distance and duration for development and quotation staging.
 * It does not represent live GPS turn-by-turn navigation or real-time traffic road network data.</p>
 */
@Service
public class FareEstimationService {

    private static final Logger logger = LoggerFactory.getLogger(FareEstimationService.class);

    private static final double EARTH_RADIUS_KM = 6371.0;
    // Urban grid detour index (urban road network is typically 20%-35% longer than straight-line)
    private static final double URBAN_ROUTING_FACTOR = 1.30;
    // Assumed average urban driving speed including traffic signals
    private static final double AVERAGE_URBAN_SPEED_KMH = 25.0;
    private static final int MINIMUM_DURATION_MINUTES = 5;
    private static final double MINIMUM_DISTANCE_KM = 0.05; // 50 meters

    public BigDecimal calculateDistanceKm(
            BigDecimal pickupLat, BigDecimal pickupLng,
            BigDecimal dropLat, BigDecimal dropLng
    ) {
        if (pickupLat == null || pickupLng == null || dropLat == null || dropLng == null) {
            throw new BadRequestException("All coordinate parameters (latitude and longitude) must be provided");
        }

        double lat1 = pickupLat.doubleValue();
        double lon1 = pickupLng.doubleValue();
        double lat2 = dropLat.doubleValue();
        double lon2 = dropLng.doubleValue();

        // Haversine great-circle distance
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double straightLineDistance = EARTH_RADIUS_KM * c;

        if (straightLineDistance < MINIMUM_DISTANCE_KM) {
            throw new BadRequestException("Pickup and destination locations cannot be identical or excessively close (minimum 50 meters required)");
        }

        double estimatedRoadDistance = straightLineDistance * URBAN_ROUTING_FACTOR;

        return BigDecimal.valueOf(estimatedRoadDistance).setScale(2, RoundingMode.HALF_UP);
    }

    public Integer calculateDurationMinutes(BigDecimal distanceKm) {
        if (distanceKm == null || distanceKm.compareTo(BigDecimal.ZERO) <= 0) {
            return MINIMUM_DURATION_MINUTES;
        }

        double distance = distanceKm.doubleValue();
        double hours = distance / AVERAGE_URBAN_SPEED_KMH;
        int minutes = (int) Math.round(hours * 60.0);

        return Math.max(minutes, MINIMUM_DURATION_MINUTES);
    }

    public FareEstimateResponse estimateFare(BigDecimal distanceKm, Integer durationMinutes, RideType rideType) {
        if (rideType == null) {
            throw new BadRequestException("Ride category must be provided for fare calculation");
        }

        BigDecimal baseFare = rideType.getBaseFare() != null ? rideType.getBaseFare() : BigDecimal.ZERO;
        BigDecimal perKmRate = rideType.getPerKmRate() != null ? rideType.getPerKmRate() : BigDecimal.ZERO;
        BigDecimal perMinuteRate = rideType.getPerMinuteRate() != null ? rideType.getPerMinuteRate() : BigDecimal.ZERO;
        BigDecimal minimumFare = rideType.getMinimumFare() != null ? rideType.getMinimumFare() : BigDecimal.ZERO;

        BigDecimal distanceFare = distanceKm.multiply(perKmRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal timeFare = BigDecimal.valueOf(durationMinutes).multiply(perMinuteRate).setScale(2, RoundingMode.HALF_UP);

        BigDecimal subtotal = baseFare.add(distanceFare).add(timeFare).setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalFare = subtotal.max(minimumFare).setScale(2, RoundingMode.HALF_UP);

        logger.debug("Estimated fare for category '{}' ({} km, {} mins): subtotal={}, minFare={}, finalFare={}",
                rideType.getName(), distanceKm, durationMinutes, subtotal, minimumFare, finalFare);

        return FareEstimateResponse.builder()
                .distanceKm(distanceKm)
                .durationMinutes(durationMinutes)
                .baseFare(baseFare)
                .distanceFare(distanceFare)
                .timeFare(timeFare)
                .subtotalFare(subtotal)
                .minimumFare(minimumFare)
                .finalEstimatedFare(finalFare)
                .currency("INR")
                .build();
    }

    public FareEstimateResponse estimateFare(
            BigDecimal pickupLat, BigDecimal pickupLng,
            BigDecimal dropLat, BigDecimal dropLng,
            RideType rideType
    ) {
        BigDecimal distanceKm = calculateDistanceKm(pickupLat, pickupLng, dropLat, dropLng);
        Integer durationMinutes = calculateDurationMinutes(distanceKm);
        return estimateFare(distanceKm, durationMinutes, rideType);
    }
}
