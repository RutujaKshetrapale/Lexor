package com.lexor.notification.service.impl;

import com.lexor.notification.dto.request.UpdatePreferencesRequest;
import com.lexor.notification.dto.response.NotificationPreferenceDto;
import com.lexor.notification.entity.NotificationPreferenceEntity;
import com.lexor.notification.exception.BadRequestException;
import com.lexor.notification.repository.NotificationPreferenceRepository;
import com.lexor.notification.service.IPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PreferenceServiceImpl implements IPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;

    @Override
    @Transactional
    public NotificationPreferenceDto getPreferences(String userUuid) {
        NotificationPreferenceEntity entity = getOrCreateEntity(userUuid);
        return mapToDto(entity);
    }

    @Override
    @Transactional
    public NotificationPreferenceEntity getOrCreateEntity(String userUuid) {
        return preferenceRepository.findByUserUuid(userUuid)
                .orElseGet(() -> {
                    NotificationPreferenceEntity newPref = NotificationPreferenceEntity.builder()
                            .preferenceUuid(UUID.randomUUID().toString())
                            .userUuid(userUuid)
                            .pushEnabled(true)
                            .emailEnabled(true)
                            .smsEnabled(true)
                            .rideUpdatesEnabled(true)
                            .paymentUpdatesEnabled(true)
                            .promotionalEnabled(false)
                            .securityAlertsEnabled(true)
                            .quietHoursEnabled(false)
                            .preferredLocale("en")
                            .build();
                    return preferenceRepository.save(newPref);
                });
    }

    @Override
    @Transactional
    public NotificationPreferenceDto updatePreferences(String userUuid, UpdatePreferencesRequest request) {
        NotificationPreferenceEntity entity = getOrCreateEntity(userUuid);

        if (request.getPushEnabled() != null) entity.setPushEnabled(request.getPushEnabled());
        if (request.getEmailEnabled() != null) entity.setEmailEnabled(request.getEmailEnabled());
        if (request.getSmsEnabled() != null) entity.setSmsEnabled(request.getSmsEnabled());
        if (request.getRideUpdatesEnabled() != null) entity.setRideUpdatesEnabled(request.getRideUpdatesEnabled());
        if (request.getPaymentUpdatesEnabled() != null) entity.setPaymentUpdatesEnabled(request.getPaymentUpdatesEnabled());
        if (request.getPromotionalEnabled() != null) entity.setPromotionalEnabled(request.getPromotionalEnabled());

        // Mandatory rule: Security alerts can never be disabled
        entity.setSecurityAlertsEnabled(true);

        if (request.getQuietHoursEnabled() != null) entity.setQuietHoursEnabled(request.getQuietHoursEnabled());
        if (request.getQuietHoursStart() != null) entity.setQuietHoursStart(request.getQuietHoursStart());
        if (request.getQuietHoursEnd() != null) entity.setQuietHoursEnd(request.getQuietHoursEnd());

        if (request.getPreferredLocale() != null) {
            String locale = request.getPreferredLocale().toLowerCase().trim();
            if (!locale.matches("^[a-z]{2}(-[A-Z]{2})?$")) {
                throw new BadRequestException("Invalid locale format. Supported formats: en, hi, mr");
            }
            entity.setPreferredLocale(locale);
        }

        NotificationPreferenceEntity updated = preferenceRepository.save(entity);
        log.info("Updated notification preferences for user {}", userUuid);
        return mapToDto(updated);
    }

    private NotificationPreferenceDto mapToDto(NotificationPreferenceEntity entity) {
        return NotificationPreferenceDto.builder()
                .preferenceUuid(entity.getPreferenceUuid())
                .userUuid(entity.getUserUuid())
                .pushEnabled(entity.getPushEnabled())
                .emailEnabled(entity.getEmailEnabled())
                .smsEnabled(entity.getSmsEnabled())
                .rideUpdatesEnabled(entity.getRideUpdatesEnabled())
                .paymentUpdatesEnabled(entity.getPaymentUpdatesEnabled())
                .promotionalEnabled(entity.getPromotionalEnabled())
                .securityAlertsEnabled(entity.getSecurityAlertsEnabled())
                .quietHoursEnabled(entity.getQuietHoursEnabled())
                .quietHoursStart(entity.getQuietHoursStart())
                .quietHoursEnd(entity.getQuietHoursEnd())
                .preferredLocale(entity.getPreferredLocale())
                .build();
    }
}
