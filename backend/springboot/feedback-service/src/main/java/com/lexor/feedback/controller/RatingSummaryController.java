package com.lexor.feedback.controller;

import com.lexor.feedback.dto.response.ApiResponse;
import com.lexor.feedback.dto.response.DriverRatingSummaryResponse;
import com.lexor.feedback.dto.response.RiderRatingSummaryResponse;
import com.lexor.feedback.service.IRatingCalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Rating Summaries", description = "Public & authorized endpoints for driver and rider rating aggregates")
public class RatingSummaryController {

    private final IRatingCalculationService ratingCalculationService;

    @Operation(summary = "Get driver rating summary and distribution")
    @GetMapping({"/api/v1/drivers/{driverUuid}/rating-summary", "/api/v1/feedback/drivers/{driverUuid}/rating-summary"})
    public ResponseEntity<ApiResponse<DriverRatingSummaryResponse>> getDriverRatingSummary(
            @PathVariable String driverUuid
    ) {
        DriverRatingSummaryResponse summary = ratingCalculationService.getDriverSummary(driverUuid);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @Operation(summary = "Get rider rating summary and distribution")
    @GetMapping({"/api/v1/riders/{riderUuid}/rating-summary", "/api/v1/feedback/riders/{riderUuid}/rating-summary"})
    public ResponseEntity<ApiResponse<RiderRatingSummaryResponse>> getRiderRatingSummary(
            @PathVariable String riderUuid
    ) {
        RiderRatingSummaryResponse summary = ratingCalculationService.getRiderSummary(riderUuid);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }
}
