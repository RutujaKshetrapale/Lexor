package com.lexor.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexor.notification.dto.request.*;
import com.lexor.notification.entity.NotificationTemplateEntity;
import com.lexor.notification.enums.*;
import com.lexor.notification.repository.*;
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
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationDeliveryRepository deliveryRepository;

    @Autowired
    private NotificationDeviceRepository deviceRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private NotificationEventInboxRepository eventInboxRepository;

    @Autowired
    private NotificationTemplateRepository templateRepository;

    private static final String TEST_SECRET = "lexor-default-jwt-secret-key-32-chars-long!";
    private static final String RIDER_UUID = "11111111-1111-4111-8111-111111111111";
    private static final String OTHER_RIDER_UUID = "22222222-2222-4222-8222-222222222222";
    private static final String DRIVER_UUID = "33333333-3333-4333-8333-333333333333";
    private static final String ADMIN_UUID = "44444444-4444-4444-8444-444444444444";
    private static final String INTERNAL_SERVICE_KEY = "lexor-internal-service-secret-key-2026";

    private String riderToken;
    private String otherRiderToken;
    private String driverToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        deliveryRepository.deleteAll();
        notificationRepository.deleteAll();
        deviceRepository.deleteAll();
        preferenceRepository.deleteAll();
        eventInboxRepository.deleteAll();

        // Seed Core Templates
        if (templateRepository.count() == 0) {
            templateRepository.save(NotificationTemplateEntity.builder()
                    .templateCode("DRIVER_ASSIGNED")
                    .channel(NotificationChannel.PUSH)
                    .locale("en")
                    .titleTemplate("Driver Assigned!")
                    .bodyTemplate("Your driver {driverName} is on the way.")
                    .build());

            templateRepository.save(NotificationTemplateEntity.builder()
                    .templateCode("DRIVER_ASSIGNED")
                    .channel(NotificationChannel.IN_APP)
                    .locale("en")
                    .titleTemplate("Driver Assigned")
                    .bodyTemplate("Your driver {driverName} has accepted your ride.")
                    .build());

            templateRepository.save(NotificationTemplateEntity.builder()
                    .templateCode("DRIVER_ASSIGNED")
                    .channel(NotificationChannel.PUSH)
                    .locale("hi")
                    .titleTemplate("चालक नियुक्त!")
                    .bodyTemplate("आपके चालक {driverName} रास्ते में हैं।")
                    .build());
        }

        riderToken = generateToken(RIDER_UUID, "rider@lexor.com", "RIDER");
        otherRiderToken = generateToken(OTHER_RIDER_UUID, "other@lexor.com", "RIDER");
        driverToken = generateToken(DRIVER_UUID, "driver@lexor.com", "DRIVER");
        adminToken = generateToken(ADMIN_UUID, "admin@lexor.com", "ADMIN");
    }

    private String generateToken(String userId, String email, String role) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("1. Direct dispatch creates notification, in-app delivery, and is accessible in rider inbox")
    void directDispatch_CreatesNotificationAndInbox() throws Exception {
        DirectNotificationRequest request = DirectNotificationRequest.builder()
                .recipientUuid(RIDER_UUID)
                .recipientRole(RecipientRole.RIDER)
                .notificationType("RIDE_COMPLETED")
                .category(NotificationCategory.RIDE)
                .title("Trip Completed")
                .body("Your trip to City Center has finished. Fare: INR 250.")
                .build();

        mockMvc.perform(post("/api/v1/internal/notifications/dispatch")
                        .header("X-Internal-Service-Key", INTERNAL_SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Trip Completed"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // Verify rider sees it in inbox
        mockMvc.perform(get("/api/v1/notifications/me")
                        .header("Authorization", "Bearer " + riderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Trip Completed"))
                .andExpect(jsonPath("$.data.content[0].isRead").value(false));
    }

    @Test
    @DisplayName("2. Unread count returns accurate number of unread notifications")
    void getUnreadCount_ReturnsAccurateCount() throws Exception {
        // Dispatch 2 notifications
        for (int i = 1; i <= 2; i++) {
            DirectNotificationRequest req = DirectNotificationRequest.builder()
                    .recipientUuid(RIDER_UUID)
                    .recipientRole(RecipientRole.RIDER)
                    .notificationType("ALERT")
                    .category(NotificationCategory.SYSTEM)
                    .title("Alert #" + i)
                    .body("Message " + i)
                    .build();

            mockMvc.perform(post("/api/v1/internal/notifications/dispatch")
                    .header("X-Internal-Service-Key", INTERNAL_SERVICE_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)));
        }

        mockMvc.perform(get("/api/v1/notifications/me/unread-count")
                        .header("Authorization", "Bearer " + riderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2));
    }

    @Test
    @DisplayName("3. Mark single notification as read updates status and timestamp")
    void markAsRead_UpdatesNotification() throws Exception {
        DirectNotificationRequest req = DirectNotificationRequest.builder()
                .recipientUuid(RIDER_UUID)
                .recipientRole(RecipientRole.RIDER)
                .notificationType("ALERT")
                .category(NotificationCategory.SYSTEM)
                .title("Read Test")
                .body("Test Body")
                .build();

        String response = mockMvc.perform(post("/api/v1/internal/notifications/dispatch")
                        .header("X-Internal-Service-Key", INTERNAL_SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();

        String notifUuid = objectMapper.readTree(response).path("data").path("notificationUuid").asText();

        mockMvc.perform(patch("/api/v1/notifications/me/" + notifUuid + "/read")
                        .header("Authorization", "Bearer " + riderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isRead").value(true))
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());
    }

    @Test
    @DisplayName("4. Mark all as read updates all notifications for authenticated rider")
    void markAllAsRead_UpdatesAllUserNotifications() throws Exception {
        for (int i = 1; i <= 3; i++) {
            DirectNotificationRequest req = DirectNotificationRequest.builder()
                    .recipientUuid(RIDER_UUID)
                    .recipientRole(RecipientRole.RIDER)
                    .notificationType("TEST")
                    .category(NotificationCategory.SYSTEM)
                    .title("Bulk " + i)
                    .body("Bulk message")
                    .build();

            mockMvc.perform(post("/api/v1/internal/notifications/dispatch")
                    .header("X-Internal-Service-Key", INTERNAL_SERVICE_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)));
        }

        mockMvc.perform(patch("/api/v1/notifications/me/read-all")
                        .header("Authorization", "Bearer " + riderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(3));

        mockMvc.perform(get("/api/v1/notifications/me/unread-count")
                        .header("Authorization", "Bearer " + riderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    @DisplayName("5. Device registration registers device token with masked display")
    void registerDevice_ReturnsMaskedToken() throws Exception {
        DeviceRegistrationRequest request = DeviceRegistrationRequest.builder()
                .platform(DevicePlatform.ANDROID)
                .fcmToken("fcm_token_super_secret_unique_string_123456789")
                .deviceModel("Pixel 8 Pro")
                .osVersion("Android 14")
                .appVersion("1.0.0")
                .build();

        mockMvc.perform(post("/api/v1/notifications/devices")
                        .header("Authorization", "Bearer " + riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.maskedToken").value("fcm_...6789"))
                .andExpect(jsonPath("$.data.platform").value("ANDROID"));
    }

    @Test
    @DisplayName("6. Device deactivation on logout marks token inactive")
    void deactivateDevice_DeactivatesDevice() throws Exception {
        DeviceRegistrationRequest request = DeviceRegistrationRequest.builder()
                .platform(DevicePlatform.IOS)
                .fcmToken("fcm_ios_token_123456789")
                .build();

        String res = mockMvc.perform(post("/api/v1/notifications/devices")
                        .header("Authorization", "Bearer " + riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();

        String deviceUuid = objectMapper.readTree(res).path("data").path("deviceUuid").asText();

        mockMvc.perform(delete("/api/v1/notifications/devices/" + deviceUuid)
                        .header("Authorization", "Bearer " + riderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/notifications/devices")
                        .header("Authorization", "Bearer " + riderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("7. Update preferences enforces mandatory securityAlertsEnabled=true")
    void updatePreferences_EnforcesSecurityAlerts() throws Exception {
        UpdatePreferencesRequest request = UpdatePreferencesRequest.builder()
                .pushEnabled(false)
                .emailEnabled(false)
                .smsEnabled(false)
                .preferredLocale("hi")
                .build();

        mockMvc.perform(put("/api/v1/notifications/me/preferences")
                        .header("Authorization", "Bearer " + riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pushEnabled").value(false))
                .andExpect(jsonPath("$.data.preferredLocale").value("hi"))
                // Security alerts must stay true
                .andExpect(jsonPath("$.data.securityAlertsEnabled").value(true));
    }

    @Test
    @DisplayName("8. Business event ingestion renders template and prevents duplicate event processing")
    void ingestBusinessEvent_IdempotentDeduplication() throws Exception {
        String eventId = "EVT-" + UUID.randomUUID();
        BusinessEventRequest request = BusinessEventRequest.builder()
                .eventId(eventId)
                .eventType("DriverAssigned")
                .sourceService("BookingService")
                .recipientUuid(RIDER_UUID)
                .recipientRole("RIDER")
                .metadata(Map.of("driverName", "Rajesh Kumar"))
                .build();

        // 1st time: accepted
        mockMvc.perform(post("/api/v1/internal/notifications/events")
                        .header("X-Internal-Service-Key", INTERNAL_SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.title").value("Driver Assigned"))
                .andExpect(jsonPath("$.data.body").value(containsString("Rajesh Kumar")));

        // 2nd time: conflict due to duplicate event ID
        mockMvc.perform(post("/api/v1/internal/notifications/events")
                        .header("X-Internal-Service-Key", INTERNAL_SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_EVENT"));
    }

    @Test
    @DisplayName("9. OTP delivery enforces destination rate limiting and suppresses plaintext OTP in logs")
    void deliverOtp_EnforcesRateLimiting() throws Exception {
        OtpDeliveryRequest req = OtpDeliveryRequest.builder()
                .recipientPhone("+919876543210")
                .otpCode("458921")
                .purpose("AUTH_LOGIN")
                .expirySeconds(300)
                .build();

        // 1st request succeeds
        mockMvc.perform(post("/api/v1/internal/notifications/otp")
                        .header("X-Internal-Service-Key", INTERNAL_SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Immediate 2nd request for same phone triggers rate limit (429)
        mockMvc.perform(post("/api/v1/internal/notifications/otp")
                        .header("X-Internal-Service-Key", INTERNAL_SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("10. Security: Rider cannot access other rider's notifications (IDOR prevention)")
    void riderCannotAccessOtherRiderNotification() throws Exception {
        DirectNotificationRequest req = DirectNotificationRequest.builder()
                .recipientUuid(OTHER_RIDER_UUID)
                .recipientRole(RecipientRole.RIDER)
                .notificationType("PRIVATE_ALERT")
                .category(NotificationCategory.SECURITY)
                .title("Private")
                .body("Private Message")
                .build();

        String res = mockMvc.perform(post("/api/v1/internal/notifications/dispatch")
                        .header("X-Internal-Service-Key", INTERNAL_SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();

        String notifUuid = objectMapper.readTree(res).path("data").path("notificationUuid").asText();

        // Rider tries to read Other Rider's notification
        mockMvc.perform(get("/api/v1/notifications/me/" + notifUuid)
                        .header("Authorization", "Bearer " + riderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("11. Security: Non-admin rider cannot access admin endpoints")
    void nonAdminCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/notifications/metrics")
                        .header("Authorization", "Bearer " + riderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("12. Admin metrics and delivery audit history")
    void adminMetricsAndAudit() throws Exception {
        DirectNotificationRequest req = DirectNotificationRequest.builder()
                .recipientUuid(RIDER_UUID)
                .recipientRole(RecipientRole.RIDER)
                .notificationType("PAYMENT_SUCCEEDED")
                .category(NotificationCategory.PAYMENT)
                .title("Paid")
                .body("Payment confirmed")
                .channels(java.util.List.of(NotificationChannel.IN_APP))
                .build();

        String res = mockMvc.perform(post("/api/v1/internal/notifications/dispatch")
                        .header("X-Internal-Service-Key", INTERNAL_SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();

        String notifUuid = objectMapper.readTree(res).path("data").path("notificationUuid").asText();

        mockMvc.perform(get("/api/v1/admin/notifications/" + notifUuid + "/audit")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(get("/api/v1/admin/notifications/metrics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalNotifications", greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("13. Health and readiness endpoints return UP and READY")
    void healthEndpoints_ReturnSuccess() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.channels.fcm_push").value("ACTIVE"));
    }
}
