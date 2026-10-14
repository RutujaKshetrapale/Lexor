package com.lexor.ridetype.service.impl;

import com.lexor.ridetype.dto.request.CreateRideTypeRequest;
import com.lexor.ridetype.dto.request.UpdateRideTypeRequest;
import com.lexor.ridetype.dto.request.UpdateRideTypeStatusRequest;
import com.lexor.ridetype.dto.response.RideTypeResponse;
import com.lexor.ridetype.entity.RideType;
import com.lexor.ridetype.exception.BadRequestException;
import com.lexor.ridetype.exception.DuplicateResourceException;
import com.lexor.ridetype.exception.ResourceNotFoundException;
import com.lexor.ridetype.repository.RideTypeRepository;
import com.lexor.ridetype.service.RideTypeService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RideTypeServiceImpl implements RideTypeService {

    private static final Logger logger = LoggerFactory.getLogger(RideTypeServiceImpl.class);

    private final RideTypeRepository rideTypeRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RideTypeResponse> getEnabledRideTypes() {
        logger.debug("Fetching all enabled ride types for rider catalogue");
        return rideTypeRepository.findByIsActiveTrueOrderByIdAsc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public RideTypeResponse getEnabledRideTypeByUuid(String uuid) {
        logger.debug("Fetching enabled ride type with UUID: {}", uuid);
        RideType rideType = rideTypeRepository.findByUuidAndIsActiveTrue(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Enabled ride type not found with UUID: " + uuid));
        return mapToResponse(rideType);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RideTypeResponse> getAllRideTypesAdmin() {
        logger.debug("Fetching all ride types (enabled and disabled) for administrator");
        return rideTypeRepository.findAllByOrderByIdAsc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public RideTypeResponse getRideTypeByUuidAdmin(String uuid) {
        logger.debug("Fetching administrative ride type details with UUID: {}", uuid);
        RideType rideType = rideTypeRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Ride type not found with UUID: " + uuid));
        return mapToResponse(rideType);
    }

    @Override
    @Transactional
    public RideTypeResponse createRideType(CreateRideTypeRequest request) {
        logger.info("Admin creating new ride type with name: '{}', code: '{}'", request.getName(), request.getCode());

        String normalizedCode = request.getCode().trim().toUpperCase();
        String normalizedName = request.getName().trim();

        validateFareConstraints(request.getBaseFare(), request.getMinimumFare(), request.getCancellationFee());

        if (rideTypeRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new DuplicateResourceException("Ride type code already exists: " + normalizedCode);
        }

        if (rideTypeRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new DuplicateResourceException("Ride type name already exists: " + normalizedName);
        }

        BigDecimal cancellationFee = request.getCancellationFee() != null ? request.getCancellationFee() : BigDecimal.ZERO;
        boolean isActive = request.getIsActive() == null || request.getIsActive();

        RideType rideType = RideType.builder()
                .uuid(UUID.randomUUID().toString())
                .name(normalizedName)
                .code(normalizedCode)
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .baseFare(request.getBaseFare())
                .perKmRate(request.getPerKmRate())
                .perMinuteRate(request.getPerMinuteRate())
                .minimumFare(request.getMinimumFare())
                .cancellationFee(cancellationFee)
                .capacity(request.getCapacity())
                .iconUrl(request.getIconUrl() != null ? request.getIconUrl().trim() : null)
                .isActive(isActive)
                .build();

        RideType saved = rideTypeRepository.save(rideType);
        logger.info("Successfully created ride type ID: {}, UUID: {}", saved.getId(), saved.getUuid());

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public RideTypeResponse updateRideType(String uuid, UpdateRideTypeRequest request) {
        logger.info("Admin updating ride type UUID: {}", uuid);

        RideType rideType = rideTypeRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Ride type not found with UUID: " + uuid));

        String normalizedName = request.getName().trim();
        if (rideTypeRepository.existsByNameIgnoreCaseAndUuidNot(normalizedName, uuid)) {
            throw new DuplicateResourceException("Ride type name already exists: " + normalizedName);
        }

        if (StringUtils.hasText(request.getCode())) {
            String normalizedCode = request.getCode().trim().toUpperCase();
            if (rideTypeRepository.existsByCodeIgnoreCaseAndUuidNot(normalizedCode, uuid)) {
                throw new DuplicateResourceException("Ride type code already exists: " + normalizedCode);
            }
            rideType.setCode(normalizedCode);
        }

        validateFareConstraints(request.getBaseFare(), request.getMinimumFare(), request.getCancellationFee());

        BigDecimal cancellationFee = request.getCancellationFee() != null ? request.getCancellationFee() : BigDecimal.ZERO;

        rideType.setName(normalizedName);
        rideType.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        rideType.setBaseFare(request.getBaseFare());
        rideType.setPerKmRate(request.getPerKmRate());
        rideType.setPerMinuteRate(request.getPerMinuteRate());
        rideType.setMinimumFare(request.getMinimumFare());
        rideType.setCancellationFee(cancellationFee);
        rideType.setCapacity(request.getCapacity());
        rideType.setIconUrl(request.getIconUrl() != null ? request.getIconUrl().trim() : null);

        RideType updated = rideTypeRepository.save(rideType);
        logger.info("Successfully updated ride type UUID: {}", updated.getUuid());

        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public RideTypeResponse updateRideTypeStatus(String uuid, UpdateRideTypeStatusRequest request) {
        logger.info("Admin setting ride type UUID: {} status to: {}", uuid, request.getIsActive());

        RideType rideType = rideTypeRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Ride type not found with UUID: " + uuid));

        if (Boolean.FALSE.equals(request.getIsActive()) && Boolean.TRUE.equals(rideType.getIsActive())) {
            long activeCount = rideTypeRepository.countByIsActiveTrue();
            if (activeCount <= 1) {
                throw new BadRequestException("Cannot disable the last active ride type on the platform");
            }
        }

        rideType.setIsActive(request.getIsActive());
        RideType updated = rideTypeRepository.save(rideType);
        logger.info("Successfully updated ride type UUID: {} isActive: {}", updated.getUuid(), updated.getIsActive());

        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public void deleteRideTypeSafely(String uuid) {
        logger.warn("Attempt to delete ride type UUID: {}", uuid);
        RideType rideType = rideTypeRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Ride type not found with UUID: " + uuid));

        // Hard deletion is prohibited to preserve referential integrity with bookings, rides, and vehicles
        throw new BadRequestException("Hard deletion of ride categories is forbidden to preserve historical records and referential integrity. Please disable the ride type instead.");
    }

    private void validateFareConstraints(BigDecimal baseFare, BigDecimal minimumFare, BigDecimal cancellationFee) {
        if (baseFare != null && baseFare.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Base fare cannot be negative");
        }
        if (minimumFare != null && minimumFare.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Minimum fare cannot be negative");
        }
        if (baseFare != null && minimumFare != null && minimumFare.compareTo(baseFare) < 0) {
            throw new BadRequestException("Minimum fare (" + minimumFare + ") cannot be less than base fare (" + baseFare + ")");
        }
        if (cancellationFee != null && cancellationFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Cancellation fee cannot be negative");
        }
    }

    private RideTypeResponse mapToResponse(RideType entity) {
        return RideTypeResponse.builder()
                .uuid(entity.getUuid())
                .name(entity.getName())
                .code(entity.getCode())
                .description(entity.getDescription())
                .baseFare(entity.getBaseFare())
                .perKmRate(entity.getPerKmRate())
                .perMinuteRate(entity.getPerMinuteRate())
                .minimumFare(entity.getMinimumFare())
                .cancellationFee(entity.getCancellationFee())
                .capacity(entity.getCapacity())
                .iconUrl(entity.getIconUrl())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
