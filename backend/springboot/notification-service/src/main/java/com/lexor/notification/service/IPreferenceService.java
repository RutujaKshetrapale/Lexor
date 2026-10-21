package com.lexor.notification.service;

import com.lexor.notification.dto.request.UpdatePreferencesRequest;
import com.lexor.notification.dto.response.NotificationPreferenceDto;
import com.lexor.notification.entity.NotificationPreferenceEntity;

public interface IPreferenceService {

    NotificationPreferenceDto getPreferences(String userUuid);

    NotificationPreferenceEntity getOrCreateEntity(String userUuid);

    NotificationPreferenceDto updatePreferences(String userUuid, UpdatePreferencesRequest request);
}
