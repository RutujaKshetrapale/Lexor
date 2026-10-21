package com.lexor.notification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMetricsResponse {
    private long totalNotifications;
    private long totalDeliveries;
    private long deliveredCount;
    private long failedCount;
    private long pendingCount;
    private long activeDevices;
    private long deadLetterEvents;
    private Map<String, Long> channelBreakdown;
}
