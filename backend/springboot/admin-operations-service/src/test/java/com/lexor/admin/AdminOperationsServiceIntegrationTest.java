package com.lexor.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexor.admin.domain.entity.AdminAuditLog;
import com.lexor.admin.domain.entity.AdminDashboardSetting;
import com.lexor.admin.dto.request.EmergencyCancelRideRequest;
import com.lexor.admin.dto.request.RecordAuditLogRequest;
import com.lexor.admin.dto.request.UpdateSettingRequest;
import com.lexor.admin.repository.AdminAuditLogRepository;
import com.lexor.admin.repository.AdminDashboardSettingRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminOperationsServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminAuditLogRepository auditLogRepository;

    @Autowired
    private AdminDashboardSettingRepository settingRepository;

    private static final String TEST_SECRET = "lexor-default-jwt-secret-key-32-chars-long!";
    private SecretKey key;

    private String adminToken;
    private String riderToken;
    private String driverToken;

    @BeforeEach
    void setUp() {
        key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

        adminToken = generateToken("admin-uuid-001", "ROLE_ADMIN", "admin@lexor.com");
        riderToken = generateToken("rider-uuid-001", "ROLE_RIDER", "rider@lexor.com");
        driverToken = generateToken("driver-uuid-001", "ROLE_DRIVER", "driver@lexor.com");

        auditLogRepository.deleteAll();
        settingRepository.deleteAll();

        // Seed initial setting
        settingRepository.save(AdminDashboardSetting.builder()
                .settingKey("LIVE_OPS_POLL_INTERVAL_SECONDS")
                .settingValue("10")
                .description("Live operations polling frequency")
                .build());
    }

    private String generateToken(String subject, String role, String email) {
        return "Bearer " + Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .claim("email", email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("Admin: Successfully retrieve executive overview with TODAY filter")
    void testGetExecutiveOverview_Today() throws Exception {
        mockMvc.perform(get("/api/v1/admin/operations/overview")
                        .param("timeRange", "TODAY")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.timeRange").value("TODAY"))
                .andExpect(jsonPath("$.data.averageDriverRating").exists())
                .andExpect(jsonPath("$.data.grossBookingValue").exists())
                .andExpect(jsonPath("$.data.metricDefinitions").isMap());
    }

    @Test
    @DisplayName("Admin: Successfully retrieve executive overview with 7D, 30D, 90D filters")
    void testGetExecutiveOverview_TimeRanges() throws Exception {
        mockMvc.perform(get("/api/v1/admin/operations/overview")
                        .param("timeRange", "7D")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timeRange").value("7D"));

        mockMvc.perform(get("/api/v1/admin/operations/overview")
                        .param("timeRange", "30D")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timeRange").value("30D"));
    }

    @Test
    @DisplayName("Admin: Reject custom date range where start date is after end date")
    void testGetExecutiveOverview_InvalidCustomRange() throws Exception {
        mockMvc.perform(get("/api/v1/admin/operations/overview")
                        .param("timeRange", "CUSTOM")
                        .param("startDate", "2026-10-25T10:00:00")
                        .param("endDate", "2026-10-20T10:00:00")
                        .header("Authorization", adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    @DisplayName("Security: Non-admin rider cannot access executive overview (403 Forbidden)")
    void testGetExecutiveOverview_RiderForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/operations/overview")
                        .header("Authorization", riderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security: Non-admin driver cannot access executive overview (403 Forbidden)")
    void testGetExecutiveOverview_DriverForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/operations/overview")
                        .header("Authorization", driverToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security: Unauthenticated request rejected (403 Forbidden)")
    void testGetExecutiveOverview_Unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/operations/overview"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin: Retrieve live operations console snapshot")
    void testGetLiveOperationsSnapshot() throws Exception {
        mockMvc.perform(get("/api/v1/admin/operations/live")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.refreshIntervalSeconds").value(10))
                .andExpect(jsonPath("$.data.driverStates").exists())
                .andExpect(jsonPath("$.data.activeRides").isArray())
                .andExpect(jsonPath("$.data.pendingRequests").isArray())
                .andExpect(jsonPath("$.data.activeAlerts").isArray());
    }

    @Test
    @DisplayName("Admin: Emergency ride cancellation creates audit log")
    void testEmergencyCancelRide() throws Exception {
        EmergencyCancelRideRequest request = EmergencyCancelRideRequest.builder()
                .bookingUuid("b901-test-booking")
                .reason("Driver safety incident reported on hotline")
                .build();

        mockMvc.perform(post("/api/v1/admin/operations/actions/cancel-ride")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(containsString("cancelled by administrator")));

        assertEquals(1, auditLogRepository.count());
        AdminAuditLog log = auditLogRepository.findAll().get(0);
        assertEquals("EMERGENCY_RIDE_CANCEL", log.getAction());
        assertEquals("b901-test-booking", log.getResourceUuid());
    }

    @Test
    @DisplayName("Admin: Retrieve platform trend analytics")
    void testGetPlatformTrends() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/trends")
                        .param("timeRange", "7D")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.dailyRides").isArray())
                .andExpect(jsonPath("$.data.hourlyDemand").isArray())
                .andExpect(jsonPath("$.data.completionRatePercentage").exists())
                .andExpect(jsonPath("$.data.cancellationRatePercentage").exists())
                .andExpect(jsonPath("$.data.starDistribution").isMap());
    }

    @Test
    @DisplayName("Admin: Stream CSV export for rides")
    void testExportCsv_Rides() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/export/csv")
                        .param("entityType", "RIDES")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"lexor-rides-export.csv\"")))
                .andExpect(content().string(containsString("Booking_UUID,Rider_UUID,Driver_UUID")));
    }

    @Test
    @DisplayName("Admin: Stream CSV export for payments")
    void testExportCsv_Payments() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/export/csv")
                        .param("entityType", "PAYMENTS")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"lexor-payments-export.csv\"")))
                .andExpect(content().string(containsString("Payment_UUID,Booking_UUID,Amount")));
    }

    @Test
    @DisplayName("Admin: Query system service health matrix")
    void testGetSystemHealth() throws Exception {
        mockMvc.perform(get("/api/v1/admin/health-monitor/services")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalServices").value(greaterThan(10)))
                .andExpect(jsonPath("$.data.services").isArray())
                .andExpect(jsonPath("$.data.services[?(@.serviceName == 'MySQL Database (lexor_db)')].status").value(hasItem("UP")));
    }

    @Test
    @DisplayName("Admin: Record and retrieve audit logs with pagination and filters")
    void testRecordAndListAuditLogs() throws Exception {
        RecordAuditLogRequest request = RecordAuditLogRequest.builder()
                .action("APPROVE_DRIVER_DOCUMENTS")
                .resourceType("DRIVER")
                .resourceUuid("d001-driver")
                .reason("Driver license and background check verified")
                .details("{\"license\":\"DL-99201\"}")
                .build();

        mockMvc.perform(post("/api/v1/admin/audit-logs")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.action").value("APPROVE_DRIVER_DOCUMENTS"));

        // Query audit logs
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("action", "APPROVE_DRIVER_DOCUMENTS")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].action").value("APPROVE_DRIVER_DOCUMENTS"));
    }

    @Test
    @DisplayName("Admin: Retrieve and update platform dashboard settings with audit trail")
    void testDashboardSettings() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs/settings")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].settingKey").value("LIVE_OPS_POLL_INTERVAL_SECONDS"))
                .andExpect(jsonPath("$.data[0].settingValue").value("10"));

        UpdateSettingRequest updateReq = UpdateSettingRequest.builder()
                .settingKey("LIVE_OPS_POLL_INTERVAL_SECONDS")
                .settingValue("15")
                .description("Updated to 15s")
                .build();

        mockMvc.perform(put("/api/v1/admin/audit-logs/settings")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settingValue").value("15"));

        // Verify audit log created
        assertEquals(1, auditLogRepository.count());
        assertEquals("UPDATE_SETTING", auditLogRepository.findAll().get(0).getAction());
    }

    @Test
    @DisplayName("Internal Service: Request with valid X-Internal-Service-Key is authorized as ADMIN")
    void testInternalServiceKey_Authorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/operations/live")
                        .header("X-Internal-Service-Key", "lexor-internal-service-secret-key-2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
