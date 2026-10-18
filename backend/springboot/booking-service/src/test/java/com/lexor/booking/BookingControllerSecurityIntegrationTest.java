package com.lexor.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexor.booking.dto.response.PageResponse;
import com.lexor.booking.security.CustomUserDetailsService;
import com.lexor.booking.security.JwtTokenProvider;
import com.lexor.booking.security.UserPrincipal;
import com.lexor.booking.service.BookingService;
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

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private BookingService bookingService;

    private String riderToken;
    private String driverToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        UserPrincipal rider = new UserPrincipal(
                1L, "rider-uuid-1", "Rajesh", "Kumar", "rider@lexor.mobility",
                "hashed", null, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_RIDER")), true
        );
        UserPrincipal driver = new UserPrincipal(
                2L, "driver-uuid-2", "Vikram", "Singh", "driver@lexor.mobility",
                "hashed", null, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_DRIVER")), true
        );
        UserPrincipal admin = new UserPrincipal(
                3L, "admin-uuid-3", "Rutuja", "Admin", "admin@lexor.mobility",
                "hashed", null, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")), true
        );

        when(customUserDetailsService.loadUserByUuid("rider-uuid-1")).thenReturn(rider);
        when(customUserDetailsService.loadUserByUuid("driver-uuid-2")).thenReturn(driver);
        when(customUserDetailsService.loadUserByUuid("admin-uuid-3")).thenReturn(admin);

        riderToken = "Bearer " + jwtTokenProvider.generateTokenFromUserPrincipal(rider);
        driverToken = "Bearer " + jwtTokenProvider.generateTokenFromUserPrincipal(driver);
        adminToken = "Bearer " + jwtTokenProvider.generateTokenFromUserPrincipal(admin);
    }

    @Test
    @DisplayName("Security: Missing token returns 401 Unauthorized")
    void testMissingToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Security: Rider cannot access driver booking endpoints (403 Forbidden)")
    void testRiderAccessDriverEndpoints_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/drivers/me/bookings")
                        .header("Authorization", riderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Security: Driver cannot create rider bookings (403 Forbidden)")
    void testDriverCreateBooking_Returns403() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rideRequestUuid\":\"any-uuid\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Security: Rider cannot access admin booking endpoints (403 Forbidden)")
    void testRiderAccessAdminEndpoints_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/bookings")
                        .header("Authorization", riderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Security: Driver cannot access admin booking endpoints (403 Forbidden)")
    void testDriverAccessAdminEndpoints_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/bookings")
                        .header("Authorization", driverToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Security: Admin successfully accesses admin bookings (200 OK)")
    void testAdminAccessAdminEndpoints_Returns200() throws Exception {
        when(bookingService.getAdminBookings(any(), any(), any(), any()))
                .thenReturn(PageResponse.of(org.springframework.data.domain.Page.empty()));

        mockMvc.perform(get("/api/v1/admin/bookings")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Security: Rider successfully accesses personal bookings (200 OK)")
    void testRiderAccessMyBookings_Returns200() throws Exception {
        when(bookingService.getMyBookings(any(), any(), any()))
                .thenReturn(PageResponse.of(org.springframework.data.domain.Page.empty()));

        mockMvc.perform(get("/api/v1/bookings/me")
                        .header("Authorization", riderToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Security: Driver successfully accesses assigned bookings (200 OK)")
    void testDriverAccessMyBookings_Returns200() throws Exception {
        when(bookingService.getDriverBookings(any(), any(), any()))
                .thenReturn(PageResponse.of(org.springframework.data.domain.Page.empty()));

        mockMvc.perform(get("/api/v1/drivers/me/bookings")
                        .header("Authorization", driverToken))
                .andExpect(status().isOk());
    }
}
