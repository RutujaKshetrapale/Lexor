package com.lexor.riderequest.service;

import com.lexor.riderequest.dto.response.PageResponse;
import com.lexor.riderequest.dto.response.RideRequestResponse;
import com.lexor.riderequest.entity.RideRequestStatus;
import org.springframework.data.domain.Pageable;

public interface AdminRideRequestService {

    PageResponse<RideRequestResponse> getAllRideRequests(Pageable pageable, RideRequestStatus status, Long riderId);

    RideRequestResponse getRideRequestByUuid(String requestUuid);
}
