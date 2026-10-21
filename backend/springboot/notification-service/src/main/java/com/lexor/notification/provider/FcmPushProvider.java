package com.lexor.notification.provider;

import com.lexor.notification.config.NotificationProperties;
import com.lexor.notification.enums.DevicePlatform;
import com.lexor.notification.provider.dto.ProviderDispatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FcmPushProvider implements IPushNotificationProvider {

    private final NotificationProperties properties;

    @Override
    public String getProviderName() {
        return "FCM_SANDBOX";
    }

    @Override
    public ProviderDispatchResult sendPushNotification(
            String deviceToken,
            DevicePlatform platform,
            String title,
            String body,
            String deepLink,
            Map<String, String> data
    ) {
        if (!properties.getNotification().getProviders().getFcm().isEnabled()) {
            log.info("FCM Push provider disabled in configuration, skipping dispatch");
            return ProviderDispatchResult.accepted("FCM_DISABLED_" + UUID.randomUUID());
        }

        // Validate token integrity
        if (deviceToken == null || deviceToken.trim().isEmpty()) {
            return ProviderDispatchResult.failure("INVALID_ARGUMENT", "Device token cannot be empty", false);
        }

        if (deviceToken.startsWith("invalid_") || deviceToken.equals("expired_token")) {
            log.warn("FCM rejected invalid/expired device token: {}", maskToken(deviceToken));
            return ProviderDispatchResult.failure("UNREGISTERED", "Device token is unregistered or expired", false);
        }

        // Simulate / Execute provider acceptance
        String messageId = "projects/" + properties.getNotification().getProviders().getFcm().getProjectId()
                + "/messages/fcm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        log.info("Dispatched FCM Push notification [messageId={}] to platform={}, token={}",
                messageId, platform, maskToken(deviceToken));

        return ProviderDispatchResult.accepted(messageId);
    }

    @Override
    public boolean isTokenInvalidError(String errorCode) {
        return "UNREGISTERED".equalsIgnoreCase(errorCode)
                || "INVALID_ARGUMENT".equalsIgnoreCase(errorCode)
                || "SENDER_ID_MISMATCH".equalsIgnoreCase(errorCode);
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 10) return "***";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}
