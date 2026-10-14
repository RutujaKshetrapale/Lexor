package com.lexor.ridetype.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexor.ridetype.entity.RideType;
import com.lexor.ridetype.entity.Role;
import com.lexor.ridetype.entity.User;
import com.lexor.ridetype.entity.UserStatus;
import com.lexor.ridetype.repository.RideTypeRepository;
import com.lexor.ridetype.repository.UserRepository;
import com.lexor.ridetype.security.JwtTokenProvider;
import com.lexor.ridetype.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RideTypeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RideTypeRepository rideTypeRepository;

    private String riderToken;
    private String driverToken;
    private String adminToken;

    private RideType activeRideType1;
    private RideType activeRideType2;
    private RideType disabledRideType;

    @BeforeEach
    void setUp() {
        rideTypeRepository.deleteAll();
        userRepository.deleteAll();

        User rider = userRepository.save(User.builder()
                .uuid(UUID.randomUUID().toString())
                .firstName("Rajesh")
                .lastName("Kumar")
                .email("rider@lexor.mobility")
                .phoneNumber("+919876543201")
                .passwordHash("$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFj5/5l.K1N6r7F0WpE0yP1O2Q3R4S5T")
                .role(Role.RIDER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        User driver = userRepository.save(User.builder()
                .uuid(UUID.randomUUID().toString())
                .firstName("Vikram")
                .lastName("Singh")
                .email("driver@lexor.mobility")
                .phoneNumber("+919876543202")
                .passwordHash("$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFj5/5l.K1N6r7F0WpE0yP1O2Q3R4S5T")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        User admin = userRepository.save(User.builder()
                .uuid(UUID.randomUUID().toString())
                .firstName("Rutuja")
                .lastName("Admin")
                .email("admin@lexor.mobility")
                .phoneNumber("+919876543203")
                .passwordHash("$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFj5/5l.K1N6r7F0WpE0yP1O2Q3R4S5T")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        riderToken = "Bearer " + jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(rider));
        driverToken = "Bearer " + jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(driver));
        adminToken = "Bearer " + jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(admin));

        activeRideType1 = rideTypeRepository.save(RideType.builder()
                .uuid("a1b2c3d4-0001-4000-8000-000000000001")
                .name("LEXOR Go")
                .code("GO")
                .description("Affordable everyday rides")
                .baseFare(new BigDecimal("50.00"))
                .perKmRate(new BigDecimal("15.00"))
                .perMinuteRate(new BigDecimal("2.50"))
                .minimumFare(new BigDecimal("80.00"))
                .cancellationFee(new BigDecimal("30.00"))
                .capacity(4)
                .iconUrl("https://cdn.lexor.mobility/icons/go.png")
                .isActive(true)
                .build());

        activeRideType2 = rideTypeRepository.save(RideType.builder()
                .uuid("a1b2c3d4-0002-4000-8000-000000000002")
                .name("LEXOR XL")
                .code("XL")
                .description("Spacious SUV rides")
                .baseFare(new BigDecimal("90.00"))
                .perKmRate(new BigDecimal("22.00"))
                .perMinuteRate(new BigDecimal("3.50"))
                .minimumFare(new BigDecimal("140.00"))
                .cancellationFee(new BigDecimal("50.00"))
                .capacity(6)
                .iconUrl("https://cdn.lexor.mobility/icons/xl.png")
                .isActive(true)
                .build());

        disabledRideType = rideTypeRepository.save(RideType.builder()
                .uuid("a1b2c3d4-0003-4000-8000-000000000003")
                .name("LEXOR Vintage")
                .code("VINTAGE")
                .description("Classic historic cars")
                .baseFare(new BigDecimal("200.00"))
                .perKmRate(new BigDecimal("50.00"))
                .perMinuteRate(new BigDecimal("10.00"))
                .minimumFare(new BigDecimal("300.00"))
                .cancellationFee(new BigDecimal("100.00"))
                .capacity(3)
                .iconUrl("https://cdn.lexor.mobility/icons/vintage.png")
                .isActive(false)
                .build());
    }

    @Test
    @DisplayName("GET /api/v1/ride-types returns enabled ride types only")
    void getAvailableRideTypes_AuthenticatedRider_ReturnsEnabledOnly() throws Exception {
        mockMvc.perform(get("/api/v1/ride-types")
                        .header("Authorization", riderToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].code", is("GO")))
                .andExpect(jsonPath("$[1].code", is("XL")))
                .andExpect(jsonPath("$[0].isActive", is(true)))
                .andExpect(jsonPath("$[1].isActive", is(true)));
    }

    @Test
    @DisplayName("GET /api/v1/ride-types allows authenticated driver and admin")
    void getAvailableRideTypes_DriverAndAdminAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/ride-types")
                        .header("Authorization", driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/v1/ride-types")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/v1/ride-types returns 401 Unauthorized for unauthenticated requests")
    void getAvailableRideTypes_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/ride-types")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("GET /api/v1/ride-types/{uuid} returns 200 OK for enabled ride type")
    void getRideTypeByUuid_Enabled_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/ride-types/" + activeRideType1.getUuid())
                        .header("Authorization", riderToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid", is(activeRideType1.getUuid())))
                .andExpect(jsonPath("$.name", is("LEXOR Go")))
                .andExpect(jsonPath("$.code", is("GO")))
                .andExpect(jsonPath("$.baseFare", is(50.0)))
                .andExpect(jsonPath("$.isActive", is(true)));
    }

    @Test
    @DisplayName("GET /api/v1/ride-types/{uuid} returns 404 for disabled ride type")
    void getRideTypeByUuid_Disabled_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/ride-types/" + disabledRideType.getUuid())
                        .header("Authorization", riderToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("GET /api/v1/ride-types/{uuid} returns 404 for unknown UUID")
    void getRideTypeByUuid_UnknownUuid_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/ride-types/non-existent-uuid-12345")
                        .header("Authorization", riderToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("GET /api/v1/ride-types/{uuid} returns 401 Unauthorized when token missing")
    void getRideTypeByUuid_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/ride-types/" + activeRideType1.getUuid())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
