package com.lexor.notification.controller;

import com.lexor.notification.dto.request.BusinessEventRequest;
import com.lexor.notification.dto.request.DirectNotificationRequest;
import com.lexor.notification.dto.request.OtpDeliveryRequest;
import com.lexor.notification.dto.response.ApiResponse;
import com.lexor.notification.dto.response.NotificationDto;
import com.lexor.notification.service.IEventIngestionService;
import com.lexor.notification.service.INotificationService;
import com.lexor.notification.service.IOtpDeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/notifications")
@RequiredArgsConstructor
@Tag(name = "Internal Service Dispatch & Ingestion", description = "Service-to-service endpoints for ingesting business events, direct notifications, and OTP delivery")
public class InternalNotificationController {

    private final IEventIngestionService eventIngestionService;
    private final INotificationService notificationService;
    private final IOtpDeliveryService otpDeliveryService;

    @PostMapping("/events")
    @Operation(summary = "Ingest business event from upstream microservice (idempotent)")
    public ResponseEntity<ApiResponse<NotificationDto>> ingestEvent(
            @Valid @RequestBody BusinessEventRequest request
    ) {
        NotificationDto result = eventIngestionService.ingestEvent(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(result, "Event accepted and notification dispatched"));
    }

    @PostMapping("/dispatch")
    @Operation(summary = "Direct transactional notification dispatch for services")
    public ResponseEntity<ApiResponse<NotificationDto>> dispatchNotification(
            @Valid @RequestBody DirectNotificationRequest request
    ) {
        NotificationDto result = notificationService.dispatchNotification(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, "Notification dispatched successfully"));
    }

    @PostMapping("/otp")
    @Operation(summary = "Deliver short-lived authentication OTP via SMS or Email (rate limited)")
    public ResponseEntity<ApiResponse<String>> deliverOtp(
            @Valid @RequestBody OtpDeliveryRequest request
    ) {
        ApiResponse<String> result = otpDeliveryService.deliverOtp(request);
        return ResponseEntity.ok(result);
    }
}
