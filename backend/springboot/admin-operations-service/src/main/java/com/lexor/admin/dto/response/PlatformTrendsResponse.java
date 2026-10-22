package com.lexor.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformTrendsResponse {

    private String timeRange;

    // Daily ride activity trends
    private List<DailyMetricPoint> dailyRides;

    // Hourly demand curve (0 - 23 hours)
    private List<HourlyDemandPoint> hourlyDemand;

    // Completion vs cancellation statistics
    private BigDecimal completionRatePercentage;
    private BigDecimal cancellationRatePercentage;

    // Financial trends
    private List<DailyFinancialPoint> dailyFinancials;

    // Feedback rating breakdown
    private Map<String, Long> starDistribution;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyMetricPoint {
        private String date; // YYYY-MM-DD
        private long totalRides;
        private long completedRides;
        private long cancelledRides;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HourlyDemandPoint {
        private int hour; // 0 - 23
        private long rideRequestsCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyFinancialPoint {
        private String date;
        private BigDecimal grossBookingValue;
        private BigDecimal collections;
        private BigDecimal refunds;
    }
}
