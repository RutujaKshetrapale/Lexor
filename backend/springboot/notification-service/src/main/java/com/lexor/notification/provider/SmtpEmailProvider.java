package com.lexor.notification.provider;

import com.lexor.notification.config.NotificationProperties;
import com.lexor.notification.provider.dto.ProviderDispatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailProvider implements IEmailNotificationProvider {

    private final NotificationProperties properties;

    @Override
    public String getProviderName() {
        return "SMTP_SANDBOX";
    }

    @Override
    public ProviderDispatchResult sendEmail(String toAddress, String subject, String bodyHtml, String bodyText) {
        if (!properties.getNotification().getProviders().getEmail().isEnabled()) {
            log.info("Email provider disabled in configuration, skipping dispatch");
            return ProviderDispatchResult.accepted("EMAIL_DISABLED_" + UUID.randomUUID());
        }

        if (toAddress == null || !toAddress.contains("@")) {
            return ProviderDispatchResult.failure("INVALID_EMAIL", "Invalid destination email address", false);
        }

        String messageId = "<" + UUID.randomUUID() + "@lexor.com>";

        log.info("Dispatched Email via {} [messageId={}] to email={}, subject='{}'",
                getProviderName(), messageId, maskEmail(toAddress), subject);

        return ProviderDispatchResult.accepted(messageId);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@");
        return parts[0].charAt(0) + "***@" + parts[1];
    }
}
