package com.lexor.notification.provider;

import com.lexor.notification.enums.DevicePlatform;
import com.lexor.notification.provider.dto.ProviderDispatchResult;

import java.util.Map;

public interface IPushNotificationProvider {

    String getProviderName();

    ProviderDispatchResult sendPushNotification(
            String deviceToken,
            DevicePlatform platform,
            String title,
            String body,
            String deepLink,
            Map<String, String> data
    );

    boolean isTokenInvalidError(String errorCode);
}
