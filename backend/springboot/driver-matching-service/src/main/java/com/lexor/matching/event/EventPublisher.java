package com.lexor.matching.event;

public interface EventPublisher {
    void publish(MatchingEvent event);
}
