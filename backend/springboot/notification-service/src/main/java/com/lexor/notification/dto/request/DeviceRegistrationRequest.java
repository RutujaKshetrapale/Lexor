package com.lexor.notification.dto.request;

import com.lexor.notification.enums.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRegistrationRequest {

    @NotNull(message = "Platform is required")
    private DevicePlatform platform;

    @NotBlank(message = "FCM device token is required")
    private String fcmToken;

    private String deviceModel;
    private String osVersion;
    private String appVersion;
}
