package com.lexor.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveOperationsSnapshotResponse {

    private LocalDateTime snapshotTimestamp;
    private int refreshIntervalSeconds;

    // Driver availability breakdown
    private DriverStateBreakdown driverStates;

    // Active in-progress rides
    private List<ActiveRideItem> activeRides;

    // Pending requests awaiting matching
    private List<PendingRequestItem> pendingRequests;

    // Recent cancellations
    private List<RecentCancellationItem> recentCancellations;

    // Critical operational alerts
    private List<OperationalAlert> activeAlerts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DriverStateBreakdown {
        private long onlineAvailable;
        private long busyInTrip;
        private long offline;
        private long totalDrivers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveRideItem {
        private String bookingUuid;
        private String rideUuid;
        private String riderUuid;
        private String driverUuid;
        private String pickupAddress;
        private String dropoffAddress;
        private String status;
        private BigDecimal estimatedFare;
        private LocalDateTime startTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingRequestItem {
        private String requestUuid;
        private String riderUuid;
        private String rideType;
        private String pickupAddress;
        private String dropoffAddress;
        private String status;
        private int matchingAttemptsCount;
        private LocalDateTime requestedAt;
        private long waitingSeconds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentCancellationItem {
        private String bookingUuid;
        private String cancelledBy;
        private String reason;
        private LocalDateTime cancelledAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationalAlert {
        private String alertType;
        private String severity; // INFO, WARNING, CRITICAL
        private String message;
        private LocalDateTime detectedAt;
    }
}
