package com.lexor.notification.provider;

import com.lexor.notification.config.NotificationProperties;
import com.lexor.notification.provider.dto.ProviderDispatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class TwilioSmsProvider implements ISmsNotificationProvider {

    private final NotificationProperties properties;

    // In-memory sliding rate limit tracker per destination number
    private final Map<String, Long> lastSentTimestampByPhone = new ConcurrentHashMap<>();

    @Override
    public String getProviderName() {
        return "TWILIO_SANDBOX";
    }

    @Override
    public ProviderDispatchResult sendSms(String destinationPhone, String messageBody) {
        if (!properties.getNotification().getProviders().getSms().isEnabled()) {
            log.info("SMS provider disabled in configuration, skipping dispatch");
            return ProviderDispatchResult.accepted("SMS_DISABLED_" + UUID.randomUUID());
        }

        if (destinationPhone == null || destinationPhone.trim().isEmpty()) {
            return ProviderDispatchResult.failure("INVALID_DESTINATION", "Destination phone cannot be empty", false);
        }

        // Simulate provider submission
        String sid = "SM" + UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        lastSentTimestampByPhone.put(destinationPhone, System.currentTimeMillis());

        log.info("Submitted SMS via {} [sid={}] to phone={}",
                getProviderName(), sid, maskPhone(destinationPhone));

        return ProviderDispatchResult.accepted(sid);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 2);
    }
}
