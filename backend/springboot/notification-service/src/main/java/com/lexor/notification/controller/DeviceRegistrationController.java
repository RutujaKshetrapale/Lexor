package com.lexor.notification.controller;

import com.lexor.notification.dto.request.DeviceRegistrationRequest;
import com.lexor.notification.dto.response.ApiResponse;
import com.lexor.notification.dto.response.DeviceDto;
import com.lexor.notification.security.UserPrincipal;
import com.lexor.notification.service.IDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications/devices")
@RequiredArgsConstructor
@Tag(name = "Device Registration", description = "Endpoints for registering and managing push notification device tokens")
public class DeviceRegistrationController {

    private final IDeviceService deviceService;

    @PostMapping
    @Operation(summary = "Register or refresh device token for push notifications")
    public ResponseEntity<ApiResponse<DeviceDto>> registerDevice(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody DeviceRegistrationRequest request
    ) {
        DeviceDto result = deviceService.registerDevice(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, "Device registered successfully"));
    }

    @GetMapping
    @Operation(summary = "List active devices registered for authenticated user")
    public ResponseEntity<ApiResponse<List<DeviceDto>>> listDevices(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<DeviceDto> devices = deviceService.listUserDevices(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(devices));
    }

    @DeleteMapping("/{deviceUuid}")
    @Operation(summary = "Deactivate/unregister device token on logout")
    public ResponseEntity<ApiResponse<Void>> deactivateDevice(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String deviceUuid
    ) {
        deviceService.deactivateDevice(principal.getUserId(), deviceUuid);
        return ResponseEntity.ok(ApiResponse.success(null, "Device deactivated successfully"));
    }
}
