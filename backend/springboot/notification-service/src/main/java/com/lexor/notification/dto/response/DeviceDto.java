package com.lexor.notification.dto.response;

import com.lexor.notification.enums.DevicePlatform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceDto {
    private String deviceUuid;
    private String userUuid;
    private DevicePlatform platform;
    private String maskedToken;
    private String deviceModel;
    private String osVersion;
    private String appVersion;
    private Boolean isActive;
    private LocalDateTime lastActiveAt;
    private LocalDateTime createdAt;
}
