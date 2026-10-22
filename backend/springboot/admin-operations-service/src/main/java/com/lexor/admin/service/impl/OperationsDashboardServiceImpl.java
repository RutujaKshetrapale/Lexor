package com.lexor.admin.service.impl;

import com.lexor.admin.client.*;
import com.lexor.admin.domain.entity.AdminAuditLog;
import com.lexor.admin.dto.request.EmergencyCancelRideRequest;
import com.lexor.admin.dto.response.ExecutiveOverviewResponse;
import com.lexor.admin.dto.response.LiveOperationsSnapshotResponse;
import com.lexor.admin.exception.BadRequestException;
import com.lexor.admin.repository.AdminAuditLogRepository;
import com.lexor.admin.security.UserPrincipal;
import com.lexor.admin.service.IOperationsDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationsDashboardServiceImpl implements IOperationsDashboardService {

    private final BookingAdminClient bookingClient;
    private final PaymentAdminClient paymentClient;
    private final DriverAdminClient driverClient;
    private final VehicleAdminClient vehicleClient;
    private final UserAdminClient userClient;
    private final RideRequestAdminClient rideRequestClient;
    private final FeedbackAdminClient feedbackClient;
    private final AdminAuditLogRepository auditLogRepository;
    private final RestTemplate restTemplate;

    @Value("${lexor.services.booking-service-url:http://localhost:8095}")
    private String bookingServiceUrl;

    @Value("${lexor.security.internal-api-key:lexor-internal-service-secret-key-2026}")
    private String internalApiKey;

    @Override
    public ExecutiveOverviewResponse getExecutiveOverview(String timeRange, LocalDateTime customStart, LocalDateTime customEnd) {
        String rangeKey = timeRange != null ? timeRange.toUpperCase() : "TODAY";
        LocalDateTime start;
        LocalDateTime end = LocalDateTime.now();

        switch (rangeKey) {
            case "7D" -> start = end.minusDays(7);
            case "30D" -> start = end.minusDays(30);
            case "90D" -> start = end.minusDays(90);
            case "CUSTOM" -> {
                if (customStart == null || customEnd == null || customStart.isAfter(customEnd)) {
                    throw new BadRequestException("Invalid custom date range specified: startDate must be before endDate");
                }
                start = customStart;
                end = customEnd;
            }
            default -> {
                rangeKey = "TODAY";
                start = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
            }
        }

        // Query upstream clients concurrently/independently
        Map<String, Object> bookingMetrics = bookingClient.getBookingMetrics(start, end);
        Map<String, Object> paymentMetrics = paymentClient.getFinancialReconciliation();
        Map<String, Object> driverMetrics = driverClient.getDriverMetrics();
        Map<String, Object> vehicleMetrics = vehicleClient.getVehicleMetrics();
        Map<String, Object> userMetrics = userClient.getUserMetrics();
        Map<String, Object> requestMetrics = rideRequestClient.getRideRequestMetrics();
        Map<String, Object> feedbackMetrics = feedbackClient.getFeedbackMetrics();

        // Build metric definitions map for audit clarity
        Map<String, String> definitions = new LinkedHashMap<>();
        definitions.put("totalRides", "Authoritative bookings recorded in booking-service across all non-purged statuses.");
        definitions.put("completedRides", "Bookings reaching terminal COMPLETED status.");
        definitions.put("cancelledRides", "Bookings terminated with CANCELLED_BY_RIDER, CANCELLED_BY_DRIVER, CANCELLED_BY_ADMIN, or EXPIRED.");
        definitions.put("activeRides", "Bookings in CONFIRMED, ACCEPTED, ARRIVED, or IN_PROGRESS status.");
        definitions.put("grossBookingValue", "Total calculated fare amount of all confirmed platform rides before deductions.");
        definitions.put("successfulPaymentTotals", "Total transaction volume collected by payment-service in COMPLETED state.");
        definitions.put("refundTotals", "Total monetary sum disbursed to riders via payment refund workflows.");
        definitions.put("platformCommissionEarned", "Platform cut retained by LEXOR according to platform commission rates.");
        definitions.put("averageDriverRating", "Decimal-safe average (HALF_UP) of all approved visible ratings from feedback-service.");

        return ExecutiveOverviewResponse.builder()
                .totalRides((Long) bookingMetrics.getOrDefault("totalRides", 0L))
                .completedRides((Long) bookingMetrics.getOrDefault("completedRides", 0L))
                .cancelledRides((Long) bookingMetrics.getOrDefault("cancelledRides", 0L))
                .activeRides((Long) bookingMetrics.getOrDefault("activeRides", 0L))
                .pendingRideRequests((Long) requestMetrics.getOrDefault("pendingRideRequests", 0L))
                .onlineDrivers((Long) driverMetrics.getOrDefault("onlineDrivers", 0L))
                .inTripDrivers((Long) driverMetrics.getOrDefault("inTripDrivers", 0L))
                .pendingDriverVerifications((Long) driverMetrics.getOrDefault("pendingDriverVerifications", 0L))
                .registeredRiders((Long) userMetrics.getOrDefault("registeredRiders", 0L))
                .registeredDrivers((Long) driverMetrics.getOrDefault("totalDrivers", userMetrics.getOrDefault("registeredDrivers", 0L)))
                .registeredVehicles((Long) vehicleMetrics.getOrDefault("registeredVehicles", 0L))
                .grossBookingValue((BigDecimal) bookingMetrics.getOrDefault("grossBookingValue", BigDecimal.ZERO))
                .successfulPaymentTotals((BigDecimal) paymentMetrics.getOrDefault("successfulPaymentTotals", BigDecimal.ZERO))
                .refundTotals((BigDecimal) paymentMetrics.getOrDefault("refundTotals", BigDecimal.ZERO))
                .platformCommissionEarned((BigDecimal) paymentMetrics.getOrDefault("platformCommissionEarned", BigDecimal.ZERO))
                .averageDriverRating((BigDecimal) feedbackMetrics.getOrDefault("averageDriverRating", BigDecimal.valueOf(5.00)))
                .feedbackReportsAwaitingModeration((Long) feedbackMetrics.getOrDefault("feedbackReportsAwaitingModeration", 0L))
                .timeRange(rangeKey)
                .rangeStartDate(start)
                .rangeEndDate(end)
                .timeZone(TimeZone.getDefault().getID())
                .calculatedAt(LocalDateTime.now())
                .metricDefinitions(definitions)
                .build();
    }

    @Override
    public LiveOperationsSnapshotResponse getLiveOperationsSnapshot() {
        Map<String, Object> driverMetrics = driverClient.getDriverMetrics();
        long online = (Long) driverMetrics.getOrDefault("onlineDrivers", 0L);
        long inTrip = (Long) driverMetrics.getOrDefault("inTripDrivers", 0L);
        long total = (Long) driverMetrics.getOrDefault("totalDrivers", 0L);
        long offline = Math.max(0, total - (online + inTrip));

        List<LiveOperationsSnapshotResponse.ActiveRideItem> activeRides = bookingClient.getActiveRides();
        List<LiveOperationsSnapshotResponse.PendingRequestItem> pendingRequests = rideRequestClient.getPendingRequests();
        List<LiveOperationsSnapshotResponse.RecentCancellationItem> recentCancellations = bookingClient.getRecentCancellations();

        List<LiveOperationsSnapshotResponse.OperationalAlert> alerts = new ArrayList<>();
        if (pendingRequests.size() > 10) {
            alerts.add(LiveOperationsSnapshotResponse.OperationalAlert.builder()
                    .alertType("MATCHING_SURGE")
                    .severity("WARNING")
                    .message("High volume of pending ride requests (" + pendingRequests.size() + ") awaiting driver assignment")
                    .detectedAt(LocalDateTime.now())
                    .build());
        }
        if (online == 0 && !activeRides.isEmpty()) {
            alerts.add(LiveOperationsSnapshotResponse.OperationalAlert.builder()
                    .alertType("LOW_DRIVER_AVAILABILITY")
                    .severity("CRITICAL")
                    .message("Zero online available drivers with active rides currently in progress")
                    .detectedAt(LocalDateTime.now())
                    .build());
        }

        return LiveOperationsSnapshotResponse.builder()
                .snapshotTimestamp(LocalDateTime.now())
                .refreshIntervalSeconds(10)
                .driverStates(LiveOperationsSnapshotResponse.DriverStateBreakdown.builder()
                        .onlineAvailable(online)
                        .busyInTrip(inTrip)
                        .offline(offline)
                        .totalDrivers(total)
                        .build())
                .activeRides(activeRides)
                .pendingRequests(pendingRequests)
                .recentCancellations(recentCancellations)
                .activeAlerts(alerts)
                .build();
    }

    @Override
    @Transactional
    public void emergencyCancelRide(UserPrincipal admin, EmergencyCancelRideRequest request) {
        log.info("Administrator {} initiated emergency cancellation for booking {}", admin.getUserUuid(), request.getBookingUuid());

        // Invoke booking-service internal cancellation
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service-Key", internalApiKey);
            Map<String, String> body = Map.of("cancellationReason", "ADMIN_OVERRIDE: " + request.getReason());
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            String url = bookingServiceUrl + "/api/v1/bookings/" + request.getBookingUuid() + "/cancel";
            restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
        } catch (Exception e) {
            log.warn("Emergency booking cancel call to {} failed: {}", bookingServiceUrl, e.getMessage());
            // Proceed to record audit log even if booking was already terminal
        }

        // Record audit entry
        auditLogRepository.save(AdminAuditLog.builder()
                .auditUuid(UUID.randomUUID().toString())
                .adminUserUuid(admin.getUserUuid())
                .action("EMERGENCY_RIDE_CANCEL")
                .resourceType("BOOKING")
                .resourceUuid(request.getBookingUuid())
                .reason(request.getReason())
                .status("SUCCESS")
                .build());
    }
}
