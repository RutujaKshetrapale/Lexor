package com.lexor.booking.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingEventPublisher implements IEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(LoggingEventPublisher.class);

    @Override
    public void publish(BookingEvent event) {
        logger.info("[EVENT] Type: {} | BookingUuid: {} | RequestUuid: {} | Payload: {}",
                event.getEventType(),
                event.getBookingUuid(),
                event.getRideRequestUuid(),
                event.getPayload());
    }
}
