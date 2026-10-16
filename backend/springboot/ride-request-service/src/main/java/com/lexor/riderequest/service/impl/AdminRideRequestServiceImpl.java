package com.lexor.riderequest.service.impl;

import com.lexor.riderequest.dto.response.PageResponse;
import com.lexor.riderequest.dto.response.RideRequestResponse;
import com.lexor.riderequest.entity.RideRequest;
import com.lexor.riderequest.entity.RideRequestStatus;
import com.lexor.riderequest.entity.RideType;
import com.lexor.riderequest.entity.User;
import com.lexor.riderequest.exception.ResourceNotFoundException;
import com.lexor.riderequest.repository.RideRequestRepository;
import com.lexor.riderequest.service.AdminRideRequestService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminRideRequestServiceImpl implements AdminRideRequestService {

    private final RideRequestRepository rideRequestRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RideRequestResponse> getAllRideRequests(Pageable pageable, RideRequestStatus status, Long riderId) {
        Specification<RideRequest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (riderId != null) {
                predicates.add(cb.equal(root.get("rider").get("id"), riderId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<RideRequest> pageResult = rideRequestRepository.findAll(spec, pageable);
        Page<RideRequestResponse> responsePage = pageResult.map(this::mapToResponse);

        return PageResponse.of(responsePage);
    }

    @Override
    @Transactional(readOnly = true)
    public RideRequestResponse getRideRequestByUuid(String requestUuid) {
        RideRequest rideRequest = rideRequestRepository.findByUuid(requestUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Ride request not found with UUID: " + requestUuid));

        return mapToResponse(rideRequest);
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
