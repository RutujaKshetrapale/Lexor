package com.lexor.admin.controller;

import com.lexor.admin.dto.response.ApiResponse;
import com.lexor.admin.dto.response.PlatformTrendsResponse;
import com.lexor.admin.service.IPlatformAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Analytics", description = "Endpoints for platform trend analytics and authenticated data export")
@SecurityRequirement(name = "BearerAuthentication")
public class AdminAnalyticsController {

    private final IPlatformAnalyticsService analyticsService;

    @Operation(summary = "Get platform trend analytics", description = "Returns daily ride volume, hourly demand curve, financial distribution, and rating distribution")
    @GetMapping("/trends")
    public ResponseEntity<ApiResponse<PlatformTrendsResponse>> getTrends(
            @Parameter(description = "Time range: 7D, 30D, 90D")
            @RequestParam(defaultValue = "7D") String timeRange
    ) {
        PlatformTrendsResponse result = analyticsService.getPlatformTrends(timeRange);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Export platform data as CSV", description = "Streams authorized CSV export of filtered domain records for offline compliance and business intelligence")
    @GetMapping(value = "/export/csv", produces = "text/csv")
    public void exportCsv(
            @Parameter(description = "Entity to export: RIDES, PAYMENTS, DRIVERS")
            @RequestParam(defaultValue = "RIDES") String entityType,
            @RequestParam(defaultValue = "7D") String timeRange,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            HttpServletResponse response
    ) throws IOException {
        response.setContentType("text/csv");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"lexor-" + entityType.toLowerCase() + "-export.csv\"");
        analyticsService.exportCsv(entityType, timeRange, startDate, endDate, response.getWriter());
    }
}
