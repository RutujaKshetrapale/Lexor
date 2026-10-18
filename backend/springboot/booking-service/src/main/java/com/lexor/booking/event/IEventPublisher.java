package com.lexor.booking.event;

public interface IEventPublisher {

    void publish(BookingEvent event);
}
