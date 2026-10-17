package com.lexor.matching.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class LoggingEventPublisher implements EventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(LoggingEventPublisher.class);

    // In-memory buffer to support verification and automated testing without external Kafka
    private final List<MatchingEvent> publishedEvents = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void publish(MatchingEvent event) {
        publishedEvents.add(event);
        logger.info("[EVENT-BUS] Published Event [{}] for RideRequest [{}]: EventId={}, Payload={}",
                event.getEventType(),
                event.getRideRequestUuid(),
                event.getEventId(),
                event.getPayload());
    }

    public List<MatchingEvent> getPublishedEvents() {
        return new ArrayList<>(publishedEvents);
    }

    public void clearEvents() {
        publishedEvents.clear();
    }
}
