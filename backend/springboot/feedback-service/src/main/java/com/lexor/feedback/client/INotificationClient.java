package com.lexor.feedback.client;

import com.lexor.feedback.dto.request.DirectNotificationRequest;

public interface INotificationClient {

    void sendNotification(DirectNotificationRequest request);
}
