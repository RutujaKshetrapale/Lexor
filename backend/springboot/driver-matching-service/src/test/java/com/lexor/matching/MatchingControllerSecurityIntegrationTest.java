package com.lexor.matching;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexor.matching.config.MatchingProperties;
import com.lexor.matching.dto.request.LocationUpdateRequest;
import com.lexor.matching.dto.response.LocationUpdateResponse;
import com.lexor.matching.dto.response.MatchingAttemptResponse;
import com.lexor.matching.entity.MatchingStatus;
import com.lexor.matching.entity.Role;
import com.lexor.matching.entity.UserStatus;
import com.lexor.matching.security.CustomUserDetailsService;
import com.lexor.matching.security.JwtTokenProvider;
import com.lexor.matching.security.UserPrincipal;
import com.lexor.matching.service.DriverLocationService;
import com.lexor.matching.service.DriverMatchingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MatchingControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DriverMatchingService driverMatchingService;

    @MockBean
    private DriverLocationService driverLocationService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private UserPrincipal adminPrincipal;
    private UserPrincipal riderPrincipal;
    private UserPrincipal driverPrincipal;

    @BeforeEach
    void setUp() {
        adminPrincipal = new UserPrincipal(
                1L, "admin-uuid", "Admin", "User", "admin@lexor.mobility",
                "pwd", Role.ADMIN, UserStatus.ACTIVE,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")), true
        );

        riderPrincipal = new UserPrincipal(
                2L, "rider-uuid", "Rajesh", "Kumar", "rider@lexor.mobility",
                "pwd", Role.RIDER, UserStatus.ACTIVE,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_RIDER")), true
        );

        driverPrincipal = new UserPrincipal(
                3L, "driver-uuid", "Vikram", "Singh", "driver@lexor.mobility",
                "pwd", Role.DRIVER, UserStatus.ACTIVE,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_DRIVER")), true
        );
    }

    @Test
    @DisplayName("Unauthenticated request to internal matching endpoint returns 401 Unauthorized")
    void testUnauthenticatedInternalMatching() throws Exception {
        mockMvc.perform(post("/api/v1/internal/matching/ride-requests/req-123/attempts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Rider attempting to call protected internal matching returns 403 Forbidden")
    void testRiderCannotInvokeInternalMatching() throws Exception {
        mockMvc.perform(post("/api/v1/internal/matching/ride-requests/req-123/attempts")
                        .with(user(riderPrincipal))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Authorized administrator successfully initiates matching attempt")
    void testAdminCanInvokeInternalMatching() throws Exception {
        MatchingAttemptResponse mockResponse = MatchingAttemptResponse.builder()
                .attemptUuid("attempt-123")
                .rideRequestUuid("req-123")
                .status(MatchingStatus.MATCHED)
                .attemptNumber(1)
                .build();

        when(driverMatchingService.initiateMatching(eq("req-123"), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/internal/matching/ride-requests/req-123/attempts")
                        .with(user(adminPrincipal))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptUuid").value("attempt-123"))
                .andExpect(jsonPath("$.status").value("MATCHED"));
    }

    @Test
    @DisplayName("Internal service call with valid X-Internal-Service-Key succeeds")
    void testInternalServiceKeyAuthentication() throws Exception {
        MatchingAttemptResponse mockResponse = MatchingAttemptResponse.builder()
                .attemptUuid("attempt-svc-123")
                .rideRequestUuid("req-123")
                .status(MatchingStatus.MATCHED)
                .attemptNumber(1)
                .build();

        when(driverMatchingService.initiateMatching(eq("req-123"), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/internal/matching/ride-requests/req-123/attempts")
                        .header("X-Internal-Service-Key", "test-internal-service-key-xyz")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptUuid").value("attempt-svc-123"));
    }

    @Test
    @DisplayName("Rider calling driver location update returns 403 Forbidden")
    void testRiderCannotSubmitDriverLocation() throws Exception {
        LocationUpdateRequest request = LocationUpdateRequest.builder()
                .latitude(new BigDecimal("12.97159870"))
                .longitude(new BigDecimal("77.59456270"))
                .build();

        mockMvc.perform(put("/api/v1/drivers/me/location")
                        .with(user(riderPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Authenticated driver successfully submits location update")
    void testDriverCanSubmitLocation() throws Exception {
        LocationUpdateRequest request = LocationUpdateRequest.builder()
                .latitude(new BigDecimal("12.97159870"))
                .longitude(new BigDecimal("77.59456270"))
                .build();

        LocationUpdateResponse mockResponse = LocationUpdateResponse.builder()
                .driverUuid(driverPrincipal.getUuid())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .status("SUCCESS")
                .build();

        when(driverLocationService.updateDriverLocation(any(), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(put("/api/v1/drivers/me/location")
                        .with(user(driverPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.driverUuid").value(driverPrincipal.getUuid()));
    }

    @Test
    @DisplayName("Driver location update with out-of-range coordinates returns 400 Bad Request with field error")
    void testValidationFailureOnDriverLocation() throws Exception {
        LocationUpdateRequest invalidRequest = LocationUpdateRequest.builder()
                .latitude(new BigDecimal("150.00000000")) // Invalid! Max 90
                .longitude(new BigDecimal("77.59456270"))
                .build();

        mockMvc.perform(put("/api/v1/drivers/me/location")
                        .with(user(driverPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.latitude").exists());
    }
}
