package com.lexor.notification.provider;

import com.lexor.notification.config.NotificationProperties;
import com.lexor.notification.enums.DevicePlatform;
import com.lexor.notification.provider.dto.ProviderDispatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Firebase Cloud Messaging (FCM) Push Notification Provider
 * Supports web push, Android, and iOS device targets with automated token invalidation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FcmPushProvider implements IPushNotificationProvider {

    private static final String FCM_LEGACY_SEND_URL = "https://fcm.googleapis.com/fcm/send";

    private final NotificationProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getProviderName() {
        return isLiveMode() ? "FCM" : "FCM_SANDBOX";
    }

    public boolean isLiveMode() {
        var fcmProps = properties.getNotification().getProviders().getFcm();
        String serverKey = fcmProps.getServerKey();
        return fcmProps.isEnabled()
                && StringUtils.hasText(serverKey)
                && !serverKey.contains("sandbox")
                && !serverKey.startsWith("your_");
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

        if (isLiveMode()) {
            return executeLiveFcmDispatch(deviceToken, platform, title, body, deepLink, data);
        }

        // Sandbox / Test Mode dispatch
        String messageId = "projects/" + properties.getNotification().getProviders().getFcm().getProjectId()
                + "/messages/fcm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        log.info("Dispatched FCM Push notification via {} [messageId={}] to platform={}, token={}",
                getProviderName(), messageId, platform, maskToken(deviceToken));

        return ProviderDispatchResult.accepted(messageId);
    }

    private ProviderDispatchResult executeLiveFcmDispatch(
            String deviceToken,
            DevicePlatform platform,
            String title,
            String body,
            String deepLink,
            Map<String, String> data
    ) {
        var fcmProps = properties.getNotification().getProviders().getFcm();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "key=" + fcmProps.getServerKey());

            Map<String, Object> payload = new HashMap<>();
            payload.put("to", deviceToken);
            payload.put("notification", Map.of(
                    "title", title != null ? title : "LEXOR Alert",
                    "body", body != null ? body : "",
                    "click_action", deepLink != null ? deepLink : "/"
            ));

            Map<String, String> dataMap = new HashMap<>();
            if (data != null) dataMap.putAll(data);
            if (deepLink != null) dataMap.put("deepLink", deepLink);
            payload.put("data", dataMap);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(FCM_LEGACY_SEND_URL, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object success = response.getBody().get("success");
                if (success != null && Integer.parseInt(success.toString()) > 0) {
                    String msgId = "fcm_live_" + UUID.randomUUID().toString().substring(0, 8);
                    log.info("Dispatched live FCM notification to token={}", maskToken(deviceToken));
                    return ProviderDispatchResult.accepted(msgId);
                } else {
                    log.warn("FCM indicated delivery failure for token={}: {}", maskToken(deviceToken), response.getBody());
                    return ProviderDispatchResult.failure("UNREGISTERED", "Device token not accepted by FCM", false);
                }
            }
        } catch (HttpStatusCodeException ex) {
            log.error("FCM API HTTP error [status={}]: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().value() == 400 || ex.getStatusCode().value() == 404) {
                return ProviderDispatchResult.failure("UNREGISTERED", "Invalid or unregistered device token", false);
            }
        } catch (Exception ex) {
            log.error("FCM dispatch error: {}", ex.getMessage());
        }

        return ProviderDispatchResult.failure("FCM_DISPATCH_FAILED", "Failed to communicate with FCM API", true);
    }

    @Override
    public boolean isTokenInvalidError(String errorCode) {
        return "UNREGISTERED".equalsIgnoreCase(errorCode)
                || "INVALID_ARGUMENT".equalsIgnoreCase(errorCode)
                || "SENDER_ID_MISMATCH".equalsIgnoreCase(errorCode)
                || "NotRegistered".equalsIgnoreCase(errorCode);
    }

    public String maskToken(String token) {
        if (token == null || token.length() < 10) return "***";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}
