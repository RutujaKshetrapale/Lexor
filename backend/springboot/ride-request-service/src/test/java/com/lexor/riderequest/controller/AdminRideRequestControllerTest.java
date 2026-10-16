package com.lexor.riderequest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexor.riderequest.entity.RideRequest;
import com.lexor.riderequest.entity.RideRequestStatus;
import com.lexor.riderequest.entity.RideType;
import com.lexor.riderequest.entity.RiderProfile;
import com.lexor.riderequest.entity.Role;
import com.lexor.riderequest.entity.User;
import com.lexor.riderequest.entity.UserStatus;
import com.lexor.riderequest.repository.RideRequestRepository;
import com.lexor.riderequest.repository.RideTypeRepository;
import com.lexor.riderequest.repository.RiderProfileRepository;
import com.lexor.riderequest.repository.UserRepository;
import com.lexor.riderequest.security.JwtTokenProvider;
import com.lexor.riderequest.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminRideRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RiderProfileRepository riderProfileRepository;

    @Autowired
    private RideTypeRepository rideTypeRepository;

    @Autowired
    private RideRequestRepository rideRequestRepository;

    private User adminUser;
    private User riderUser;
    private User driverUser;

    private RiderProfile riderProfile;
    private RideType goRideType;

    private String adminToken;
    private String riderToken;
    private String driverToken;

    private RideRequest sampleRequest;

    @BeforeEach
    void setUp() {
        rideRequestRepository.deleteAll();
        riderProfileRepository.deleteAll();
        rideTypeRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Admin User
        adminUser = userRepository.save(User.builder()
                .uuid(UUID.randomUUID().toString())
                .firstName("Rutuja")
                .lastName("Admin")
                .email("admin@lexor.mobility")
                .phoneNumber("+919876543210")
                .passwordHash("$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFj5/5l.K1N6r7F0WpE0yP1O2Q3R4S5T")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        // 2. Rider User & Profile
        riderUser = userRepository.save(User.builder()
                .uuid(UUID.randomUUID().toString())
                .firstName("Rajesh")
                .lastName("Kumar")
                .email("rajesh.kumar@example.com")
                .phoneNumber("+919876543211")
                .passwordHash("$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFj5/5l.K1N6r7F0WpE0yP1O2Q3R4S5T")
                .role(Role.RIDER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        riderProfile = riderProfileRepository.save(RiderProfile.builder()
                .user(riderUser)
                .rating(new BigDecimal("4.85"))
                .totalRides(10)
                .preferredPaymentMethod("UPI")
                .build());

        // 3. Driver User
        driverUser = userRepository.save(User.builder()
                .uuid(UUID.randomUUID().toString())
                .firstName("Vikram")
                .lastName("Singh")
                .email("vikram.singh@example.com")
                .phoneNumber("+919876543212")
                .passwordHash("$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFj5/5l.K1N6r7F0WpE0yP1O2Q3R4S5T")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        // 4. Ride Type
        goRideType = rideTypeRepository.save(RideType.builder()
                .name("LEXOR Go")
                .code("GO")
                .baseFare(new BigDecimal("50.00"))
                .perKmRate(new BigDecimal("15.00"))
                .perMinuteRate(new BigDecimal("2.50"))
                .minimumFare(new BigDecimal("80.00"))
                .cancellationFee(new BigDecimal("30.00"))
                .capacity(4)
                .isActive(true)
                .build());

        // 5. Tokens
        adminToken = "Bearer " + jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(adminUser));
        riderToken = "Bearer " + jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(riderUser));
        driverToken = "Bearer " + jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(driverUser));

        // 6. Sample Request
        sampleRequest = rideRequestRepository.save(RideRequest.builder()
                .uuid(UUID.randomUUID().toString())
                .rider(riderProfile)
                .rideType(goRideType)
                .pickupAddress("MG Road, Bengaluru")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Prestige Tech Park, Bengaluru")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .estimatedDistanceKm(new BigDecimal("11.50"))
                .estimatedDurationMinutes(32)
                .estimatedFare(new BigDecimal("250.00"))
                .status(RideRequestStatus.REQUESTED)
                .requestedAt(LocalDateTime.now())
                .build());
    }

    @Test
    @DisplayName("Admin lists all platform ride requests with pagination (200)")
    void testAdminGetAllRideRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ride-requests")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].requestUuid", is(sampleRequest.getUuid())));
    }

    @Test
    @DisplayName("Admin filters ride requests by status")
    void testAdminFilterByStatus() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ride-requests?status=REQUESTED")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)));

        mockMvc.perform(get("/api/v1/admin/ride-requests?status=CANCELLED")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    @Test
    @DisplayName("Admin retrieves request details by UUID (200)")
    void testAdminGetByUuidSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ride-requests/" + sampleRequest.getUuid())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestUuid", is(sampleRequest.getUuid())))
                .andExpect(jsonPath("$.riderEmail", is("rajesh.kumar@example.com")))
                .andExpect(jsonPath("$.rideTypeCode", is("GO")));
    }

    @Test
    @DisplayName("Admin querying unknown UUID returns 404")
    void testAdminGetUnknownUuidReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ride-requests/" + UUID.randomUUID())
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("Rider attempting to access admin endpoints is forbidden (403)")
    void testRiderCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ride-requests")
                        .header("Authorization", riderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("Driver attempting to access admin endpoints is forbidden (403)")
    void testDriverCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ride-requests")
                        .header("Authorization", driverToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("Unauthenticated request to admin endpoints returns 401")
    void testUnauthenticatedAdminAccessReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ride-requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }
}
