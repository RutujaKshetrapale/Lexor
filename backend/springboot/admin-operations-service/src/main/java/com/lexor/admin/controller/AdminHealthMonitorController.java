package com.lexor.admin.controller;

import com.lexor.admin.dto.response.ApiResponse;
import com.lexor.admin.dto.response.ServiceHealthStatusResponse;
import com.lexor.admin.service.IServiceHealthMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/health-monitor")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Health Monitor", description = "Operational diagnostics matrix querying the live health of all platform microservices, database, and cache")
@SecurityRequirement(name = "BearerAuthentication")
public class AdminHealthMonitorController {

    private final IServiceHealthMonitorService healthMonitorService;

    @Operation(summary = "Get full system health matrix", description = "Inspects live responsiveness, ports, readiness, and latencies across all 12 platform microservices and infrastructure components")
    @GetMapping("/services")
    public ResponseEntity<ApiResponse<ServiceHealthStatusResponse>> getSystemHealth() {
        ServiceHealthStatusResponse result = healthMonitorService.getSystemHealth();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
