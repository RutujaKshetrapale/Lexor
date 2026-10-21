package com.lexor.notification.service;

import com.lexor.notification.dto.request.DeviceRegistrationRequest;
import com.lexor.notification.dto.response.DeviceDto;
import com.lexor.notification.entity.NotificationDeviceEntity;

import java.util.List;

public interface IDeviceService {

    DeviceDto registerDevice(String userUuid, DeviceRegistrationRequest request);

    List<DeviceDto> listUserDevices(String userUuid);

    List<NotificationDeviceEntity> getActiveDeviceEntities(String userUuid);

    void deactivateDevice(String userUuid, String deviceUuid);

    void deactivateInvalidToken(String fcmToken);
}
