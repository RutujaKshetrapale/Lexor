package com.lexor.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutiveOverviewResponse {

    // Metric 1: Total rides across platform
    private long totalRides;

    // Metric 2: Completed rides (COMPLETED status)
    private long completedRides;

    // Metric 3: Cancelled rides (CANCELLED_BY_RIDER, CANCELLED_BY_DRIVER, CANCELLED_BY_ADMIN)
    private long cancelledRides;

    // Metric 4: Currently active rides (CONFIRMED, ACCEPTED, ARRIVED, IN_PROGRESS)
    private long activeRides;

    // Metric 5: Pending ride requests awaiting driver matching
    private long pendingRideRequests;

    // Metric 6: Online & available drivers (ONLINE status in driver_profiles)
    private long onlineDrivers;

    // Metric 7: Drivers in trip
    private long inTripDrivers;

    // Metric 8: Pending driver verifications awaiting admin document approval
    private long pendingDriverVerifications;

    // Metric 9: Registered riders (users with role RIDER)
    private long registeredRiders;

    // Metric 10: Registered drivers (users with role DRIVER)
    private long registeredDrivers;

    // Metric 11: Registered vehicles in fleet
    private long registeredVehicles;

    // Metric 12: Gross booking value (sum of total_fare for all confirmed bookings)
    private BigDecimal grossBookingValue;

    // Metric 13: Successful payment totals (sum of completed payment transactions)
    private BigDecimal successfulPaymentTotals;

    // Metric 14: Total refunds disbursed
    private BigDecimal refundTotals;

    // Metric 15: Platform commission earned
    private BigDecimal platformCommissionEarned;

    // Metric 16: Average driver rating across platform (1.00 - 5.00)
    private BigDecimal averageDriverRating;

    // Metric 17: Feedback reports awaiting administrative moderation
    private long feedbackReportsAwaitingModeration;

    // Metadata & Time Range
    private String timeRange;
    private LocalDateTime rangeStartDate;
    private LocalDateTime rangeEndDate;
    private String timeZone;
    private LocalDateTime calculatedAt;

    // Metric Definitions and Calculation Rules
    private Map<String, String> metricDefinitions;

    // Upstream Resilience & Partial Data Indicators (Phase 14.1)
    @com.fasterxml.jackson.annotation.JsonProperty("isPartialData")
    private boolean isPartialData;
    private java.util.List<String> unavailableSources;
}
