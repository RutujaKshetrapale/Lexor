package com.lexor.matching.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchingEvent {

    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    private String eventType;

    private String rideRequestUuid;

    private String correlationId;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    private Map<String, Object> payload;
}
