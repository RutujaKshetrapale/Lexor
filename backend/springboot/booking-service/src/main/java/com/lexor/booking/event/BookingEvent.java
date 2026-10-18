package com.lexor.booking.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingEvent {

    private String eventType;
    private String bookingUuid;
    private String rideRequestUuid;
    private String correlationId;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    private Map<String, Object> payload;
}
