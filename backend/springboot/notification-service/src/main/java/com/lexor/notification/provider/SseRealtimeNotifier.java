package com.lexor.notification.provider;

import com.lexor.notification.dto.response.NotificationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class SseRealtimeNotifier implements IRealtimeNotifier {

    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(String userUuid) {
        SseEmitter emitter = new SseEmitter(180000L); // 3 minutes timeout

        activeEmitters.put(userUuid, emitter);

        emitter.onCompletion(() -> activeEmitters.remove(userUuid));
        emitter.onTimeout(() -> {
            emitter.complete();
            activeEmitters.remove(userUuid);
        });
        emitter.onError(e -> activeEmitters.remove(userUuid));

        try {
            emitter.send(SseEmitter.event().name("INIT").data("Connected to LEXOR Notification Stream"));
        } catch (IOException e) {
            log.warn("Failed to send initial SSE to user {}: {}", userUuid, e.getMessage());
            activeEmitters.remove(userUuid);
        }

        return emitter;
    }

    @Override
    public void notifyUser(String userUuid, NotificationDto notification) {
        SseEmitter emitter = activeEmitters.get(userUuid);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("NOTIFICATION")
                        .data(notification));
                log.debug("Delivered real-time SSE notification to user {}", userUuid);
            } catch (IOException e) {
                log.warn("Failed to deliver SSE notification to user {}: {}", userUuid, e.getMessage());
                emitter.complete();
                activeEmitters.remove(userUuid);
            }
        }
    }
}
