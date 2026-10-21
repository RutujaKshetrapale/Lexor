package com.lexor.notification.controller;

import com.lexor.notification.dto.response.*;
import com.lexor.notification.service.INotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Notification Operations", description = "Endpoints for notification monitoring, delivery audits, retries, and platform metrics")
public class AdminNotificationController {

    private final INotificationService notificationService;

    @GetMapping
    @Operation(summary = "Admin: Global paginated notification history")
    public ResponseEntity<ApiResponse<PageResponse<NotificationDto>>> listAllNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<NotificationDto> result = notificationService.adminListNotifications(page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{notificationUuid}/audit")
    @Operation(summary = "Admin: Get channel delivery attempts and provider audit log for a notification")
    public ResponseEntity<ApiResponse<List<DeliveryAuditDto>>> getDeliveryAudit(
            @PathVariable String notificationUuid
    ) {
        List<DeliveryAuditDto> result = notificationService.adminGetDeliveryAudit(notificationUuid);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{notificationUuid}/retry")
    @Operation(summary = "Admin: Manually trigger retry for failed notification deliveries")
    public ResponseEntity<ApiResponse<NotificationDto>> retryDelivery(
            @PathVariable String notificationUuid
    ) {
        NotificationDto result = notificationService.adminRetryDelivery(notificationUuid);
        return ResponseEntity.ok(ApiResponse.success(result, "Delivery retry scheduled successfully"));
    }

    @GetMapping("/metrics")
    @Operation(summary = "Admin: Get notification delivery metrics and channel breakdown")
    public ResponseEntity<ApiResponse<NotificationMetricsResponse>> getMetrics() {
        NotificationMetricsResponse result = notificationService.adminGetMetrics();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
