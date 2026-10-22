package com.lexor.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceHealthStatusResponse {

    private LocalDateTime checkedAt;
    private int totalServices;
    private int healthyServices;
    private int degradedServices;
    private int downServices;
    private String overallSystemStatus; // UP, DEGRADED, DOWN

    private List<ServiceHealthItem> services;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceHealthItem {
        private String serviceName;
        private int port;
        private String endpointUrl;
        private String status; // UP, DEGRADED, DOWN
        private long latencyMs;
        private String readiness; // READY, NOT_READY
        private String errorDetails;
        private LocalDateTime lastChecked;
    }
}
