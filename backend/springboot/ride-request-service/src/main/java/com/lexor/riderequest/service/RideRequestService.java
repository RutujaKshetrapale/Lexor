package com.lexor.riderequest.service;

import com.lexor.riderequest.dto.request.CancelRideRequest;
import com.lexor.riderequest.dto.request.CreateRideRequest;
import com.lexor.riderequest.dto.response.PageResponse;
import com.lexor.riderequest.dto.response.RideRequestResponse;
import com.lexor.riderequest.entity.RideRequestStatus;
import com.lexor.riderequest.security.UserPrincipal;
import org.springframework.data.domain.Pageable;

public interface RideRequestService {

    RideRequestResponse createRideRequest(UserPrincipal riderPrincipal, CreateRideRequest request, String bearerToken);

    PageResponse<RideRequestResponse> getMyRideRequests(UserPrincipal riderPrincipal, Pageable pageable, RideRequestStatus status);

    RideRequestResponse getMyRideRequestByUuid(UserPrincipal riderPrincipal, String requestUuid);

    RideRequestResponse cancelRideRequest(UserPrincipal riderPrincipal, String requestUuid, CancelRideRequest request);
}
