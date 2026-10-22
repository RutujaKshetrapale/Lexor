package com.lexor.admin.controller;

import com.lexor.admin.dto.request.EmergencyCancelRideRequest;
import com.lexor.admin.dto.response.ApiResponse;
import com.lexor.admin.dto.response.ExecutiveOverviewResponse;
import com.lexor.admin.dto.response.LiveOperationsSnapshotResponse;
import com.lexor.admin.security.UserPrincipal;
import com.lexor.admin.service.IOperationsDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/admin/operations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Operations", description = "Endpoints for executive overview metrics, live operations console, and emergency interventions")
@SecurityRequirement(name = "BearerAuthentication")
public class AdminOperationsController {

    private final IOperationsDashboardService dashboardService;

    @Operation(summary = "Get executive dashboard overview metrics", description = "Aggregates multi-service KPIs across rides, revenue, fleet, and moderation with date-range filters")
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<ExecutiveOverviewResponse>> getOverview(
            @Parameter(description = "Time range filter: TODAY, 7D, 30D, 90D, or CUSTOM")
            @RequestParam(defaultValue = "TODAY") String timeRange,
            @Parameter(description = "Start datetime for CUSTOM range (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End datetime for CUSTOM range (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        ExecutiveOverviewResponse result = dashboardService.getExecutiveOverview(timeRange, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Get live operations console snapshot", description = "Returns real-time platform operational pulse: active rides, matching delays, driver availability breakdown, and active alerts")
    @GetMapping("/live")
    public ResponseEntity<ApiResponse<LiveOperationsSnapshotResponse>> getLiveSnapshot() {
        LiveOperationsSnapshotResponse result = dashboardService.getLiveOperationsSnapshot();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Emergency administrative ride cancellation", description = "Privileged intervention to cancel an in-progress or stranded ride with mandatory audit justification")
    @PostMapping("/actions/cancel-ride")
    public ResponseEntity<ApiResponse<String>> emergencyCancelRide(
            @AuthenticationPrincipal UserPrincipal admin,
            @Valid @RequestBody EmergencyCancelRideRequest request
    ) {
        dashboardService.emergencyCancelRide(admin, request);
        return ResponseEntity.ok(ApiResponse.success("Booking " + request.getBookingUuid() + " cancelled by administrator"));
    }
}
