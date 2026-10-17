package com.lexor.matching.service.impl;

import com.lexor.matching.config.MatchingProperties;
import com.lexor.matching.dto.request.LocationUpdateRequest;
import com.lexor.matching.dto.response.LocationUpdateResponse;
import com.lexor.matching.entity.DriverLocation;
import com.lexor.matching.entity.DriverProfile;
import com.lexor.matching.entity.DriverStatus;
import com.lexor.matching.exception.BadRequestException;
import com.lexor.matching.exception.ResourceNotFoundException;
import com.lexor.matching.repository.DriverLocationRepository;
import com.lexor.matching.repository.DriverProfileRepository;
import com.lexor.matching.security.UserPrincipal;
import com.lexor.matching.service.DriverLocationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class DriverLocationServiceImpl implements DriverLocationService {

    private static final Logger logger = LoggerFactory.getLogger(DriverLocationServiceImpl.class);

    private final DriverProfileRepository driverProfileRepository;
    private final DriverLocationRepository driverLocationRepository;
    private final MatchingProperties matchingProperties;

    // High-performance in-memory cache for fast sub-millisecond driver proximity lookup
    private final Map<Long, DriverLocation> locationCache = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public LocationUpdateResponse updateDriverLocation(UserPrincipal driverPrincipal, LocationUpdateRequest request) {
        DriverProfile driver = driverProfileRepository.findByUserUuid(driverPrincipal.getUuid())
                .orElseThrow(() -> new ResourceNotFoundException("Driver profile not found for user: " + driverPrincipal.getUuid()));

        // Validate coordinates
        if (request.getLatitude() == null || request.getLongitude() == null) {
            throw new BadRequestException("Latitude and longitude coordinates are mandatory");
        }
        if (request.getLatitude().compareTo(new BigDecimal("-90.0")) < 0 || request.getLatitude().compareTo(new BigDecimal("90.0")) > 0) {
            throw new BadRequestException("Latitude must be between -90.0 and 90.0 degrees");
        }
        if (request.getLongitude().compareTo(new BigDecimal("-180.0")) < 0 || request.getLongitude().compareTo(new BigDecimal("180.0")) > 0) {
            throw new BadRequestException("Longitude must be between -180.0 and 180.0 degrees");
        }

        LocalDateTime recordedAt = request.getTimestamp() != null ? request.getTimestamp() : LocalDateTime.now();

        // Prevent timestamps far into the future (allow 60 seconds clock skew)
        if (recordedAt.isAfter(LocalDateTime.now().plusSeconds(60))) {
            throw new BadRequestException("Location timestamp cannot be in the future");
        }

        // Save persistent record in database
        DriverLocation locationRecord = DriverLocation.builder()
                .driver(driver)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .heading(request.getHeading())
                .speedKmh(request.getSpeedKmh())
                .recordedAt(recordedAt)
                .build();

        DriverLocation saved = driverLocationRepository.save(locationRecord);

        // Update in-memory location cache
        locationCache.put(driver.getId(), saved);

        logger.debug("Ingested location for driver [{}] at lat={}, lon={}, recordedAt={}",
                driver.getId(), saved.getLatitude(), saved.getLongitude(), recordedAt);

        return LocationUpdateResponse.builder()
                .driverUuid(driverPrincipal.getUuid())
                .latitude(saved.getLatitude())
                .longitude(saved.getLongitude())
                .heading(saved.getHeading())
                .speedKmh(saved.getSpeedKmh())
                .recordedAt(saved.getRecordedAt())
                .status("SUCCESS")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DriverLocation> getLatestLocation(Long driverId) {
        DriverLocation cached = locationCache.get(driverId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<DriverLocation> fromDb = driverLocationRepository.findFirstByDriverIdOrderByRecordedAtDesc(driverId);
        fromDb.ifPresent(loc -> locationCache.put(driverId, loc));
        return fromDb;
    }

    @Override
    public boolean isLocationFresh(DriverLocation location, long maxAgeSeconds) {
        if (location == null || location.getRecordedAt() == null) {
            return false;
        }
        Duration age = Duration.between(location.getRecordedAt(), LocalDateTime.now());
        return !age.isNegative() && age.getSeconds() <= maxAgeSeconds;
    }
}
