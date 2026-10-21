package com.lexor.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@Tag(name = "Health Probes", description = "Liveness and readiness probes")
public class NotificationHealthController {

    @GetMapping("/health")
    @Operation(summary = "Microservice liveness probe")
    public ResponseEntity<Map<String, Object>> liveness() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "notification-service",
                "timestamp", LocalDateTime.now()
        ));
    }

    @GetMapping("/health/ready")
    @Operation(summary = "Microservice readiness probe")
    public ResponseEntity<Map<String, Object>> readiness() {
        return ResponseEntity.ok(Map.of(
                "status", "READY",
                "service", "notification-service",
                "channels", Map.of(
                        "in_app", "ACTIVE",
                        "fcm_push", "ACTIVE",
                        "sms", "ACTIVE",
                        "email", "ACTIVE"
                ),
                "timestamp", LocalDateTime.now()
        ));
    }
}
