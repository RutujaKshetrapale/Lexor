package com.lexor.notification.service.impl;

import com.lexor.notification.entity.NotificationTemplateEntity;
import com.lexor.notification.enums.NotificationChannel;
import com.lexor.notification.repository.NotificationTemplateRepository;
import com.lexor.notification.service.ITemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateServiceImpl implements ITemplateService {

    private final NotificationTemplateRepository templateRepository;

    @Override
    public RenderedTemplate render(
            String templateCode,
            NotificationChannel channel,
            String preferredLocale,
            Map<String, Object> variables
    ) {
        String locale = (preferredLocale != null && !preferredLocale.trim().isEmpty())
                ? preferredLocale.toLowerCase().trim()
                : "en";

        // 1. Try finding requested locale
        Optional<NotificationTemplateEntity> templateOpt = templateRepository
                .findByTemplateCodeAndChannelAndLocaleAndIsActiveTrue(templateCode, channel, locale);

        // 2. Fall back to English 'en' if requested locale is missing
        if (templateOpt.isEmpty() && !"en".equals(locale)) {
            templateOpt = templateRepository
                    .findByTemplateCodeAndChannelAndLocaleAndIsActiveTrue(templateCode, channel, "en");
        }

        String rawTitle;
        String rawBody;

        if (templateOpt.isPresent()) {
            NotificationTemplateEntity entity = templateOpt.get();
            rawTitle = entity.getTitleTemplate();
            rawBody = entity.getBodyTemplate();
        } else {
            // Built-in resilient defaults when template table doesn't have an exact match
            rawTitle = generateDefaultTitle(templateCode);
            rawBody = generateDefaultBody(templateCode, variables);
        }

        String interpolatedTitle = interpolate(rawTitle, variables, false);
        String interpolatedBody = interpolate(rawBody, variables, channel == NotificationChannel.EMAIL);

        return new RenderedTemplate(interpolatedTitle, interpolatedBody);
    }

    private String interpolate(String text, Map<String, Object> variables, boolean escapeHtml) {
        if (text == null) return "";
        if (variables == null || variables.isEmpty()) return text;

        String result = text;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String key = "{" + entry.getKey() + "}";
            String val = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
            if (escapeHtml) {
                val = HtmlUtils.htmlEscape(val);
            }
            result = result.replace(key, val);
        }
        return result;
    }

    private String generateDefaultTitle(String templateCode) {
        if (templateCode == null) return "LEXOR Notification";
        return switch (templateCode) {
            case "DRIVER_ASSIGNED" -> "Driver Assigned";
            case "DRIVER_ARRIVED" -> "Driver Arrived";
            case "RIDE_STARTED" -> "Ride Started";
            case "RIDE_COMPLETED" -> "Ride Completed";
            case "RIDE_CANCELLED" -> "Ride Cancelled";
            case "PAYMENT_SUCCEEDED" -> "Payment Successful";
            case "PAYMENT_FAILED" -> "Payment Failed";
            case "REFUND_COMPLETED" -> "Refund Processed";
            case "SECURITY_ALERT" -> "Security Alert";
            case "DRIVER_OFFER" -> "New Ride Offer";
            default -> "LEXOR Alert";
        };
    }

    private String generateDefaultBody(String templateCode, Map<String, Object> variables) {
        if (variables != null && variables.containsKey("message")) {
            return String.valueOf(variables.get("message"));
        }
        return "You have an update regarding your LEXOR mobility account.";
    }
}
