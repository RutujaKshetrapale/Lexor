package com.lexor.notification.service;

import com.lexor.notification.enums.NotificationChannel;

import java.util.Map;

public interface ITemplateService {

    RenderedTemplate render(
            String templateCode,
            NotificationChannel channel,
            String preferredLocale,
            Map<String, Object> variables
    );

    record RenderedTemplate(String title, String body) {}
}
