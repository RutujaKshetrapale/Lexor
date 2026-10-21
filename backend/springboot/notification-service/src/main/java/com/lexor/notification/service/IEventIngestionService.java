package com.lexor.notification.service;

import com.lexor.notification.dto.request.BusinessEventRequest;
import com.lexor.notification.dto.response.NotificationDto;

public interface IEventIngestionService {

    NotificationDto ingestEvent(BusinessEventRequest request);
}
