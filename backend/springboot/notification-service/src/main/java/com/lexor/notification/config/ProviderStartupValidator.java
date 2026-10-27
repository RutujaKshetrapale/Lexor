package com.lexor.notification.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * Validates external provider configuration at startup.
 * When production mode is active, ensures required provider credentials exist
 * rather than silently failing or falling back to fake/sandbox paths.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProviderStartupValidator implements CommandLineRunner {

    private final NotificationProperties properties;
    private final Environment environment;

    @Override
    public void run(String... args) {
        boolean isProduction = Arrays.asList(environment.getActiveProfiles()).contains("prod")
                || Arrays.asList(environment.getActiveProfiles()).contains("production")
                || "production".equalsIgnoreCase(environment.getProperty("APP_ENV"));

        var smsProps = properties.getNotification().getProviders().getSms();
        var fcmProps = properties.getNotification().getProviders().getFcm();

        log.info("Notification Service startup: Active SMS Provider: {}, Active FCM Provider: {}, Production Mode: {}",
                smsProps.getProvider(), fcmProps.isEnabled() ? "FCM" : "DISABLED", isProduction);

        if (isProduction) {
            // Validate SMS in Production
            if (smsProps.isEnabled() && !smsProps.getProvider().toUpperCase().contains("SANDBOX")) {
                if (smsProps.getProvider().toUpperCase().contains("MSG91")) {
                    String authKey = smsProps.getMsg91().getAuthKey();
                    if (!StringUtils.hasText(authKey) || authKey.startsWith("your_")) {
                        throw new IllegalStateException("Production Startup Validation Failed: MSG91_AUTH_KEY is missing or invalid for production SMS integration.");
                    }
                } else if (smsProps.getProvider().toUpperCase().contains("TWILIO")) {
                    String sid = smsProps.getAccountSid();
                    String token = smsProps.getAuthToken();
                    if (!StringUtils.hasText(sid) || !StringUtils.hasText(token) || sid.startsWith("your_")) {
                        throw new IllegalStateException("Production Startup Validation Failed: TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN are required for production Twilio integration.");
                    }
                }
            }

            // Validate FCM in Production
            if (fcmProps.isEnabled()) {
                String serverKey = fcmProps.getServerKey();
                if (!StringUtils.hasText(serverKey) || serverKey.contains("sandbox") || serverKey.startsWith("your_")) {
                    log.warn("FCM Server Key is not configured for production push notifications.");
                }
            }
        }
    }
}
