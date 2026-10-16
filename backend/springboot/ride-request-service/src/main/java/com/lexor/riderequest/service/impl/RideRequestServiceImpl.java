package com.lexor.riderequest.service.impl;

import com.lexor.riderequest.client.RideTypeClient;
import com.lexor.riderequest.client.dto.RideTypeDto;
import com.lexor.riderequest.dto.request.CancelRideRequest;
import com.lexor.riderequest.dto.request.CreateRideRequest;
import com.lexor.riderequest.dto.response.FareEstimateResponse;
import com.lexor.riderequest.dto.response.PageResponse;
import com.lexor.riderequest.dto.response.RideRequestResponse;
import com.lexor.riderequest.entity.RideRequest;
import com.lexor.riderequest.entity.RideRequestStatus;
import com.lexor.riderequest.entity.RideType;
import com.lexor.riderequest.entity.RiderProfile;
import com.lexor.riderequest.entity.Role;
import com.lexor.riderequest.entity.User;
import com.lexor.riderequest.exception.BadRequestException;
import com.lexor.riderequest.exception.ConflictException;
import com.lexor.riderequest.exception.ForbiddenException;
import com.lexor.riderequest.exception.ResourceNotFoundException;
import com.lexor.riderequest.repository.RideRequestRepository;
import com.lexor.riderequest.repository.RideTypeRepository;
import com.lexor.riderequest.repository.RiderProfileRepository;
import com.lexor.riderequest.repository.UserRepository;
import com.lexor.riderequest.security.UserPrincipal;
import com.lexor.riderequest.service.FareEstimationService;
import com.lexor.riderequest.service.RideRequestService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RideRequestServiceImpl implements RideRequestService {

    private static final Logger logger = LoggerFactory.getLogger(RideRequestServiceImpl.class);

    private final RideRequestRepository rideRequestRepository;
    private final RiderProfileRepository riderProfileRepository;
    private final RideTypeRepository rideTypeRepository;
    private final UserRepository userRepository;
    private final FareEstimationService fareEstimationService;
    private final RideTypeClient rideTypeClient;

    @Override
    @Transactional
    public RideRequestResponse createRideRequest(UserPrincipal riderPrincipal, CreateRideRequest request, String bearerToken) {
        if (riderPrincipal.getRole() != Role.RIDER) {
            throw new ForbiddenException("Only authenticated users with role RIDER are permitted to create ride requests");
        }

        User user = userRepository.findByUuid(riderPrincipal.getUuid())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with UUID: " + riderPrincipal.getUuid()));

        RiderProfile riderProfile = getOrCreateRiderProfile(user);

        // Resolve and validate Ride Type category
        RideType rideType = resolveRideType(request, bearerToken);

        if (Boolean.FALSE.equals(rideType.getIsActive())) {
            throw new BadRequestException("Ride category '" + rideType.getName() + "' is currently disabled and unavailable for booking.");
        }

        // Calculate route distance, duration, and estimated fare
        FareEstimateResponse fareEstimate = fareEstimationService.estimateFare(
                request.getPickupLatitude(), request.getPickupLongitude(),
                request.getDropLatitude(), request.getDropLongitude(),
                rideType
        );

        RideRequest rideRequest = RideRequest.builder()
                .uuid(UUID.randomUUID().toString())
                .rider(riderProfile)
                .rideType(rideType)
                .pickupAddress(request.getPickupAddress().trim())
                .pickupLatitude(request.getPickupLatitude())
                .pickupLongitude(request.getPickupLongitude())
                .dropAddress(request.getDropAddress().trim())
                .dropLatitude(request.getDropLatitude())
                .dropLongitude(request.getDropLongitude())
                .estimatedDistanceKm(fareEstimate.getDistanceKm())
                .estimatedDurationMinutes(fareEstimate.getDurationMinutes())
                .estimatedFare(fareEstimate.getFinalEstimatedFare())
                .couponId(request.getCouponId())
                .status(RideRequestStatus.REQUESTED)
                .requestedAt(LocalDateTime.now())
                .build();

        RideRequest savedRequest = rideRequestRepository.save(rideRequest);
        logger.info("Created ride request [{}] for rider [{}] with category [{}]",
                savedRequest.getUuid(), riderPrincipal.getEmail(), rideType.getCode());

        return mapToResponse(savedRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RideRequestResponse> getMyRideRequests(UserPrincipal riderPrincipal, Pageable pageable, RideRequestStatus status) {
        RiderProfile riderProfile = riderProfileRepository.findByUserId(riderPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Rider profile not found for user: " + riderPrincipal.getEmail()));

        Page<RideRequest> pageResult;
        if (status != null) {
            pageResult = rideRequestRepository.findByRiderIdAndStatus(riderProfile.getId(), status, pageable);
        } else {
            pageResult = rideRequestRepository.findByRiderId(riderProfile.getId(), pageable);
        }

        Page<RideRequestResponse> responsePage = pageResult.map(this::mapToResponse);
        return PageResponse.of(responsePage);
    }

    @Override
    @Transactional(readOnly = true)
    public RideRequestResponse getMyRideRequestByUuid(UserPrincipal riderPrincipal, String requestUuid) {
        RideRequest rideRequest = rideRequestRepository.findByUuid(requestUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Ride request not found with UUID: " + requestUuid));

        // Enforce ownership: Rider can only view their own request
        if (!rideRequest.getRider().getUser().getId().equals(riderPrincipal.getId())) {
            logger.warn("Rider [{}] attempted unauthorized access to ride request [{}] owned by rider [{}]",
                    riderPrincipal.getEmail(), requestUuid, rideRequest.getRider().getUser().getEmail());
            throw new ForbiddenException("You do not have permission to view this ride request");
        }

        return mapToResponse(rideRequest);
    }

    @Override
    @Transactional
    public RideRequestResponse cancelRideRequest(UserPrincipal riderPrincipal, String requestUuid, CancelRideRequest request) {
        RideRequest rideRequest = rideRequestRepository.findByUuid(requestUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Ride request not found with UUID: " + requestUuid));

        // Enforce ownership: Rider can only cancel their own request
        if (!rideRequest.getRider().getUser().getId().equals(riderPrincipal.getId())) {
            logger.warn("Rider [{}] attempted unauthorized cancellation of ride request [{}]",
                    riderPrincipal.getEmail(), requestUuid);
            throw new ForbiddenException("You do not have permission to cancel this ride request");
        }

        // Validate state transitions
        if (rideRequest.getStatus() == RideRequestStatus.CANCELLED) {
            throw new ConflictException("Ride request is already cancelled.");
        }
        if (rideRequest.getStatus() == RideRequestStatus.MATCHED) {
            throw new ConflictException("Ride request has already been matched with a driver and cannot be cancelled through this endpoint.");
        }
        if (rideRequest.getStatus() == RideRequestStatus.EXPIRED) {
            throw new ConflictException("Ride request has expired and cannot be cancelled.");
        }
        if (!rideRequest.getStatus().canCancel()) {
            throw new ConflictException("Ride request cannot be cancelled in status: " + rideRequest.getStatus());
        }

        rideRequest.setStatus(RideRequestStatus.CANCELLED);
        RideRequest updatedRequest = rideRequestRepository.save(rideRequest);

        logger.info("Rider [{}] successfully cancelled ride request [{}] (Reason: {})",
                riderPrincipal.getEmail(), requestUuid, request != null ? request.getReason() : "None provided");

        return mapToResponse(updatedRequest);
    }

    private RiderProfile getOrCreateRiderProfile(User user) {
        return riderProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    RiderProfile newProfile = RiderProfile.builder()
                            .user(user)
                            .rating(new BigDecimal("5.00"))
                            .totalRides(0)
                            .preferredPaymentMethod("UPI")
                            .build();
                    return riderProfileRepository.save(newProfile);
                });
    }

    private RideType resolveRideType(CreateRideRequest request, String bearerToken) {
        // 1. Attempt lookup by rideTypeUuid via RideTypeClient
        if (StringUtils.hasText(request.getRideTypeUuid())) {
            try {
                Optional<RideTypeDto> externalDto = rideTypeClient.getRideTypeByUuid(request.getRideTypeUuid().trim(), bearerToken);
                if (externalDto.isPresent()) {
                    String code = externalDto.get().getCode();
                    return rideTypeRepository.findByCode(code)
                            .orElseThrow(() -> new ResourceNotFoundException("Ride category '" + code + "' was verified via service but not found in platform database"));
                }
            } catch (Exception ex) {
                logger.warn("Could not resolve ride type via client ({}): falling back to local database lookup", ex.getMessage());
            }
        }

        // 2. Direct lookup by category code
        if (StringUtils.hasText(request.getRideTypeCode())) {
            return rideTypeRepository.findByCode(request.getRideTypeCode().trim().toUpperCase())
                    .orElseThrow(() -> new ResourceNotFoundException("Ride category with code '" + request.getRideTypeCode() + "' not found"));
        }

        // 3. Fallback: If UUID was supplied and happens to match a code or lookup
        if (StringUtils.hasText(request.getRideTypeUuid())) {
            Optional<RideType> byCode = rideTypeRepository.findByCode(request.getRideTypeUuid().trim().toUpperCase());
            if (byCode.isPresent()) {
                return byCode.get();
            }
        }

        throw new BadRequestException("Must specify a valid 'rideTypeUuid' or 'rideTypeCode' (e.g. 'GO', 'XL', 'PREMIER', 'GREEN')");
    }

    private RideRequestResponse mapToResponse(RideRequest request) {
        User user = request.getRider().getUser();
        RideType rideType = request.getRideType();

        return RideRequestResponse.builder()
                .requestUuid(request.getUuid())
                .riderUuid(user.getUuid())
                .riderName(user.getFirstName() + " " + user.getLastName())
                .riderEmail(user.getEmail())
                .pickupAddress(request.getPickupAddress())
                .pickupLatitude(request.getPickupLatitude())
                .pickupLongitude(request.getPickupLongitude())
                .dropAddress(request.getDropAddress())
                .dropLatitude(request.getDropLatitude())
                .dropLongitude(request.getDropLongitude())
                .rideTypeCode(rideType.getCode())
                .rideTypeName(rideType.getName())
                .estimatedDistanceKm(request.getEstimatedDistanceKm())
                .estimatedDurationMinutes(request.getEstimatedDurationMinutes())
                .estimatedFare(request.getEstimatedFare())
                .currency("INR")
                .status(request.getStatus())
                .requestedAt(request.getRequestedAt())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }
}
