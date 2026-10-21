package com.lexor.notification.provider;

import com.lexor.notification.dto.response.NotificationDto;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface IRealtimeNotifier {

    SseEmitter subscribe(String userUuid);

    void notifyUser(String userUuid, NotificationDto notification);
}
