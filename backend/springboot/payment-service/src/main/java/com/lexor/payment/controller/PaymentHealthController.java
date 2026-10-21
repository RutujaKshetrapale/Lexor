package com.lexor.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/health")
@Tag(name = "Health & Diagnostics", description = "Liveness and readiness probes")
public class PaymentHealthController {

    @Operation(summary = "Liveness check", description = "Verifies payment microservice process is alive")
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "payment-service",
                "timestamp", LocalDateTime.now()
        ));
    }

    @Operation(summary = "Readiness check", description = "Verifies payment microservice is ready to accept traffic")
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        return ResponseEntity.ok(Map.of(
                "status", "READY",
                "service", "payment-service",
                "timestamp", LocalDateTime.now()
        ));
    }
}
