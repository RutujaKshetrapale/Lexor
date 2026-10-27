package com.lexor.notification.provider;

import com.lexor.notification.config.NotificationProperties;
import com.lexor.notification.provider.dto.ProviderDispatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Primary routing SMS provider.
 * Dynamically routes to MSG91 or Twilio based on configuration,
 * eliminating duplicate business logic while supporting India-oriented
 * MSG91 primary launch and global Twilio extensibility.
 */
@Component
@Primary
@RequiredArgsConstructor
@Slf4j
public class SmsProviderRouter implements ISmsNotificationProvider {

    private final Msg91SmsProvider msg91SmsProvider;
    private final TwilioSmsProvider twilioSmsProvider;
    private final NotificationProperties properties;

    @Override
    public String getProviderName() {
        return getActiveProvider().getProviderName();
    }

    @Override
    public ProviderDispatchResult sendSms(String destinationPhone, String messageBody) {
        ISmsNotificationProvider active = getActiveProvider();
        log.info("Routing SMS dispatch to provider: {}", active.getProviderName());
        return active.sendSms(destinationPhone, messageBody);
    }

    public ISmsNotificationProvider getActiveProvider() {
        String configured = properties.getNotification().getProviders().getSms().getProvider();
        if (configured != null && configured.toUpperCase().contains("TWILIO")) {
            return twilioSmsProvider;
        }
        // Default to MSG91 for India launch
        return msg91SmsProvider;
    }
}
