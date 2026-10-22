package com.lexor.admin.service;

import com.lexor.admin.dto.request.EmergencyCancelRideRequest;
import com.lexor.admin.dto.response.ExecutiveOverviewResponse;
import com.lexor.admin.dto.response.LiveOperationsSnapshotResponse;
import com.lexor.admin.security.UserPrincipal;

import java.time.LocalDateTime;

public interface IOperationsDashboardService {

    ExecutiveOverviewResponse getExecutiveOverview(String timeRange, LocalDateTime customStart, LocalDateTime customEnd);

    LiveOperationsSnapshotResponse getLiveOperationsSnapshot();

    void emergencyCancelRide(UserPrincipal admin, EmergencyCancelRideRequest request);
}
