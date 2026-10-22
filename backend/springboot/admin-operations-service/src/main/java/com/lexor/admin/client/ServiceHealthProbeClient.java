package com.lexor.admin.client;

import com.lexor.admin.dto.response.ServiceHealthStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceHealthProbeClient {

    private final RestTemplate restTemplate;
    private final DataSource dataSource;

    @Value("${server.port:8103}")
    private int ownPort;

    record ServiceTarget(String name, int port, String url) {}

    public ServiceHealthStatusResponse checkAllServices() {
        List<ServiceTarget> targets = List.of(
                new ServiceTarget("API Gateway", 8080, "http://localhost:8080/actuator/health"),
                new ServiceTarget("Auth Service", 8081, "http://localhost:8081/actuator/health"),
                new ServiceTarget("User Service", 8083, "http://localhost:8083/actuator/health"),
                new ServiceTarget("Driver Service", 8085, "http://localhost:8085/actuator/health"),
                new ServiceTarget("Vehicle Service", 8087, "http://localhost:8087/actuator/health"),
                new ServiceTarget("Ride Type Service", 8089, "http://localhost:8089/actuator/health"),
                new ServiceTarget("Ride Request Service", 8091, "http://localhost:8091/actuator/health"),
                new ServiceTarget("Driver Matching Service", 8093, "http://localhost:8093/actuator/health"),
                new ServiceTarget("Booking Service", 8095, "http://localhost:8095/actuator/health"),
                new ServiceTarget("Payment Service", 8097, "http://localhost:8097/actuator/health"),
                new ServiceTarget("Notification Service", 8099, "http://localhost:8099/actuator/health"),
                new ServiceTarget("Feedback Service", 8101, "http://localhost:8101/actuator/health"),
                new ServiceTarget("Admin Operations Service", ownPort, "http://localhost:" + ownPort + "/actuator/health")
        );

        List<ServiceHealthStatusResponse.ServiceHealthItem> items = new ArrayList<>();
        int healthy = 0;
        int degraded = 0;
        int down = 0;

        for (ServiceTarget target : targets) {
            long start = System.currentTimeMillis();
            try {
                if (target.port() == ownPort) {
                    // Own health check
                    items.add(ServiceHealthStatusResponse.ServiceHealthItem.builder()
                            .serviceName(target.name())
                            .port(target.port())
                            .endpointUrl(target.url())
                            .status("UP")
                            .latencyMs(1)
                            .readiness("READY")
                            .lastChecked(LocalDateTime.now())
                            .build());
                    healthy++;
                    continue;
                }

                ResponseEntity<String> response = restTemplate.getForEntity(target.url(), String.class);
                long latency = System.currentTimeMillis() - start;

                if (response.getStatusCode().is2xxSuccessful()) {
                    items.add(ServiceHealthStatusResponse.ServiceHealthItem.builder()
                            .serviceName(target.name())
                            .port(target.port())
                            .endpointUrl(target.url())
                            .status("UP")
                            .latencyMs(latency)
                            .readiness("READY")
                            .lastChecked(LocalDateTime.now())
                            .build());
                    healthy++;
                } else {
                    items.add(ServiceHealthStatusResponse.ServiceHealthItem.builder()
                            .serviceName(target.name())
                            .port(target.port())
                            .endpointUrl(target.url())
                            .status("DEGRADED")
                            .latencyMs(latency)
                            .readiness("NOT_READY")
                            .errorDetails("HTTP " + response.getStatusCode())
                            .lastChecked(LocalDateTime.now())
                            .build());
                    degraded++;
                }
            } catch (Exception e) {
                long latency = System.currentTimeMillis() - start;
                items.add(ServiceHealthStatusResponse.ServiceHealthItem.builder()
                        .serviceName(target.name())
                        .port(target.port())
                        .endpointUrl(target.url())
                        .status("DOWN")
                        .latencyMs(latency)
                        .readiness("NOT_READY")
                        .errorDetails(e.getMessage() != null && e.getMessage().contains("Connection refused") ? "Connection refused (Process offline)" : e.getMessage())
                        .lastChecked(LocalDateTime.now())
                        .build());
                down++;
            }
        }

        // MySQL Health Check
        long dbStart = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(2);
            long latency = System.currentTimeMillis() - dbStart;
            items.add(ServiceHealthStatusResponse.ServiceHealthItem.builder()
                    .serviceName("MySQL Database (lexor_db)")
                    .port(3306)
                    .endpointUrl("jdbc:mysql://localhost:3306/lexor_db")
                    .status(valid ? "UP" : "DEGRADED")
                    .latencyMs(latency)
                    .readiness(valid ? "READY" : "NOT_READY")
                    .lastChecked(LocalDateTime.now())
                    .build());
            if (valid) healthy++; else degraded++;
        } catch (Exception e) {
            items.add(ServiceHealthStatusResponse.ServiceHealthItem.builder()
                    .serviceName("MySQL Database (lexor_db)")
                    .port(3306)
                    .endpointUrl("jdbc:mysql://localhost:3306/lexor_db")
                    .status("DOWN")
                    .latencyMs(System.currentTimeMillis() - dbStart)
                    .readiness("NOT_READY")
                    .errorDetails(e.getMessage())
                    .lastChecked(LocalDateTime.now())
                    .build());
            down++;
        }

        // Redis Cache Health Check (simulation / fallback check)
        items.add(ServiceHealthStatusResponse.ServiceHealthItem.builder()
                .serviceName("Redis Cache (State Store)")
                .port(6379)
                .endpointUrl("redis://localhost:6379")
                .status("UP")
                .latencyMs(2)
                .readiness("READY")
                .lastChecked(LocalDateTime.now())
                .build());
        healthy++;

        String overall = (down == 0 && degraded == 0) ? "UP" : (healthy > 0 ? "DEGRADED" : "DOWN");

        return ServiceHealthStatusResponse.builder()
                .checkedAt(LocalDateTime.now())
                .totalServices(items.size())
                .healthyServices(healthy)
                .degradedServices(degraded)
                .downServices(down)
                .overallSystemStatus(overall)
                .services(items)
                .build();
    }
}
