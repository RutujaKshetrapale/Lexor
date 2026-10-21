package com.lexor.notification.controller;

import com.lexor.notification.dto.request.UpdatePreferencesRequest;
import com.lexor.notification.dto.response.*;
import com.lexor.notification.enums.NotificationCategory;
import com.lexor.notification.provider.IRealtimeNotifier;
import com.lexor.notification.security.UserPrincipal;
import com.lexor.notification.service.INotificationService;
import com.lexor.notification.service.IPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications/me")
@RequiredArgsConstructor
@Tag(name = "User In-App Notifications", description = "Endpoints for rider and driver in-app notification inbox, preferences, and real-time streaming")
public class RiderNotificationController {

    private final INotificationService notificationService;
    private final IPreferenceService preferenceService;
    private final IRealtimeNotifier realtimeNotifier;

    @GetMapping
    @Operation(summary = "Get user notification inbox (paginated)")
    public ResponseEntity<ApiResponse<PageResponse<NotificationDto>>> getInbox(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(required = false) NotificationCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<NotificationDto> result = notificationService.getUserInbox(
                principal.getUserId(), isRead, category, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get count of unread notifications")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UnreadCountResponse result = notificationService.getUnreadCount(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{notificationUuid}")
    @Operation(summary = "Get notification details by UUID")
    public ResponseEntity<ApiResponse<NotificationDto>> getNotification(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String notificationUuid
    ) {
        NotificationDto result = notificationService.getNotification(principal.getUserId(), notificationUuid);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PatchMapping("/{notificationUuid}/read")
    @Operation(summary = "Mark single notification as read")
    public ResponseEntity<ApiResponse<NotificationDto>> markAsRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String notificationUuid
    ) {
        NotificationDto result = notificationService.markAsRead(principal.getUserId(), notificationUuid);
        return ResponseEntity.ok(ApiResponse.success(result, "Notification marked as read"));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<ApiResponse<Integer>> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        int count = notificationService.markAllAsRead(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(count, "Marked " + count + " notifications as read"));
    }

    @GetMapping("/preferences")
    @Operation(summary = "Get notification preferences")
    public ResponseEntity<ApiResponse<NotificationPreferenceDto>> getPreferences(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        NotificationPreferenceDto result = preferenceService.getPreferences(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PutMapping("/preferences")
    @Operation(summary = "Update notification preferences")
    public ResponseEntity<ApiResponse<NotificationPreferenceDto>> updatePreferences(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdatePreferencesRequest request
    ) {
        NotificationPreferenceDto result = preferenceService.updatePreferences(principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(result, "Preferences updated successfully"));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to real-time Server-Sent Events (SSE) notification stream")
    public SseEmitter subscribeStream(@AuthenticationPrincipal UserPrincipal principal) {
        return realtimeNotifier.subscribe(principal.getUserId());
    }
}
