package com.lexor.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessEventRequest {

    @NotBlank(message = "Event ID is required")
    private String eventId;

    @NotBlank(message = "Event type is required")
    private String eventType;

    @NotBlank(message = "Source service is required")
    private String sourceService;

    private String recipientUuid;
    private String recipientRole;
    private String relatedEntityType;
    private String relatedEntityUuid;

    private Map<String, Object> metadata;
}
