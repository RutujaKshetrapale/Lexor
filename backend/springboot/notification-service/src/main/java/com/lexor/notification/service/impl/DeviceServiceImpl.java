package com.lexor.notification.service.impl;

import com.lexor.notification.dto.request.DeviceRegistrationRequest;
import com.lexor.notification.dto.response.DeviceDto;
import com.lexor.notification.entity.NotificationDeviceEntity;
import com.lexor.notification.exception.ForbiddenException;
import com.lexor.notification.exception.ResourceNotFoundException;
import com.lexor.notification.repository.NotificationDeviceRepository;
import com.lexor.notification.service.IDeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceServiceImpl implements IDeviceService {

    private final NotificationDeviceRepository deviceRepository;

    @Override
    @Transactional
    public DeviceDto registerDevice(String userUuid, DeviceRegistrationRequest request) {
        String token = request.getFcmToken().trim();

        // Check if token already exists for this or another user
        Optional<NotificationDeviceEntity> existingOpt = deviceRepository.findByFcmToken(token);

        NotificationDeviceEntity entity;
        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
            // If registered to another user, reassign to current authenticated user
            entity.setUserUuid(userUuid);
            entity.setPlatform(request.getPlatform());
            entity.setDeviceModel(request.getDeviceModel());
            entity.setOsVersion(request.getOsVersion());
            entity.setAppVersion(request.getAppVersion());
            entity.setIsActive(true);
            entity.setLastActiveAt(LocalDateTime.now());
        } else {
            entity = NotificationDeviceEntity.builder()
                    .deviceUuid(UUID.randomUUID().toString())
                    .userUuid(userUuid)
                    .platform(request.getPlatform())
                    .fcmToken(token)
                    .deviceModel(request.getDeviceModel())
                    .osVersion(request.getOsVersion())
                    .appVersion(request.getAppVersion())
                    .isActive(true)
                    .lastActiveAt(LocalDateTime.now())
                    .build();
        }

        NotificationDeviceEntity saved = deviceRepository.save(entity);
        log.info("Registered device {} for user {}", saved.getDeviceUuid(), userUuid);
        return mapToDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceDto> listUserDevices(String userUuid) {
        return deviceRepository.findByUserUuidAndIsActiveTrue(userUuid).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDeviceEntity> getActiveDeviceEntities(String userUuid) {
        return deviceRepository.findByUserUuidAndIsActiveTrue(userUuid);
    }

    @Override
    @Transactional
    public void deactivateDevice(String userUuid, String deviceUuid) {
        NotificationDeviceEntity device = deviceRepository.findByDeviceUuid(deviceUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceUuid));

        if (!device.getUserUuid().equals(userUuid)) {
            throw new ForbiddenException("Cannot deactivate device belonging to another user");
        }

        device.setIsActive(false);
        deviceRepository.save(device);
        log.info("Deactivated device {} for user {}", deviceUuid, userUuid);
    }

    @Override
    @Transactional
    public void deactivateInvalidToken(String fcmToken) {
        deviceRepository.findByFcmToken(fcmToken).ifPresent(device -> {
            device.setIsActive(false);
            deviceRepository.save(device);
            log.warn("Auto-deactivated invalid device token for device {}", device.getDeviceUuid());
        });
    }

    private DeviceDto mapToDto(NotificationDeviceEntity entity) {
        String token = entity.getFcmToken();
        String maskedToken = (token != null && token.length() > 8)
                ? token.substring(0, 4) + "..." + token.substring(token.length() - 4)
                : "***";

        return DeviceDto.builder()
                .deviceUuid(entity.getDeviceUuid())
                .userUuid(entity.getUserUuid())
                .platform(entity.getPlatform())
                .maskedToken(maskedToken)
                .deviceModel(entity.getDeviceModel())
                .osVersion(entity.getOsVersion())
                .appVersion(entity.getAppVersion())
                .isActive(entity.getIsActive())
                .lastActiveAt(entity.getLastActiveAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
