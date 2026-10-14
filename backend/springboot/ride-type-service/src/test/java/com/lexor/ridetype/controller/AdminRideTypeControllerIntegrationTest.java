package com.lexor.ridetype.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexor.ridetype.dto.request.CreateRideTypeRequest;
import com.lexor.ridetype.dto.request.UpdateRideTypeRequest;
import com.lexor.ridetype.dto.request.UpdateRideTypeStatusRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminRideTypeControllerIntegrationTest {

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

    private String adminToken;
    private String riderToken;
    private String driverToken;

    private RideType activeRideType;
    private RideType secondActiveRideType;
    private RideType disabledRideType;

    @BeforeEach
    void setUp() {
        rideTypeRepository.deleteAll();
        userRepository.deleteAll();

        User admin = userRepository.save(User.builder()
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

        User rider = userRepository.save(User.builder()
                .uuid(UUID.randomUUID().toString())
                .firstName("Rajesh")
                .lastName("Kumar")
                .email("rider@lexor.mobility")
                .phoneNumber("+919876543211")
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
                .phoneNumber("+919876543212")
                .passwordHash("$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFj5/5l.K1N6r7F0WpE0yP1O2Q3R4S5T")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        adminToken = "Bearer " + jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(admin));
        riderToken = "Bearer " + jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(rider));
        driverToken = "Bearer " + jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(driver));

        activeRideType = rideTypeRepository.save(RideType.builder()
                .uuid("a1b2c3d4-0001-4000-8000-000000000001")
                .name("LEXOR Go")
                .code("GO")
                .description("Affordable rides")
                .baseFare(new BigDecimal("50.00"))
                .perKmRate(new BigDecimal("15.00"))
                .perMinuteRate(new BigDecimal("2.50"))
                .minimumFare(new BigDecimal("80.00"))
                .cancellationFee(new BigDecimal("30.00"))
                .capacity(4)
                .iconUrl("https://cdn.lexor.mobility/icons/go.png")
                .isActive(true)
                .build());

        secondActiveRideType = rideTypeRepository.save(RideType.builder()
                .uuid("a1b2c3d4-0002-4000-8000-000000000002")
                .name("LEXOR XL")
                .code("XL")
                .description("Spacious SUV")
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
                .name("LEXOR Premier")
                .code("PREMIER")
                .description("Luxury sedans")
                .baseFare(new BigDecimal("120.00"))
                .perKmRate(new BigDecimal("28.00"))
                .perMinuteRate(new BigDecimal("4.50"))
                .minimumFare(new BigDecimal("180.00"))
                .cancellationFee(new BigDecimal("70.00"))
                .capacity(4)
                .iconUrl("https://cdn.lexor.mobility/icons/premier.png")
                .isActive(false)
                .build());
    }

    @Test
    @DisplayName("POST /api/v1/admin/ride-types creates valid ride type")
    void createRideType_AsAdmin_Returns201() throws Exception {
        CreateRideTypeRequest request = CreateRideTypeRequest.builder()
                .name("LEXOR Green")
                .code("GREEN")
                .description("Eco-friendly hybrid rides")
                .baseFare(new BigDecimal("60.00"))
                .perKmRate(new BigDecimal("16.00"))
                .perMinuteRate(new BigDecimal("2.80"))
                .minimumFare(new BigDecimal("90.00"))
                .cancellationFee(new BigDecimal("35.00"))
                .capacity(4)
                .iconUrl("https://cdn.lexor.mobility/icons/green.png")
                .isActive(true)
                .build();

        mockMvc.perform(post("/api/v1/admin/ride-types")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("LEXOR Green")))
                .andExpect(jsonPath("$.code", is("GREEN")))
                .andExpect(jsonPath("$.baseFare", is(60.0)))
                .andExpect(jsonPath("$.capacity", is(4)))
                .andExpect(jsonPath("$.isActive", is(true)));
    }

    @Test
    @DisplayName("POST /api/v1/admin/ride-types rejected with 403 for RIDER and DRIVER")
    void createRideType_AsNonAdmin_Returns403() throws Exception {
        CreateRideTypeRequest request = CreateRideTypeRequest.builder()
                .name("Test Type")
                .code("TEST")
                .baseFare(new BigDecimal("50.00"))
                .perKmRate(new BigDecimal("15.00"))
                .perMinuteRate(new BigDecimal("2.50"))
                .minimumFare(new BigDecimal("80.00"))
                .capacity(4)
                .build();

        mockMvc.perform(post("/api/v1/admin/ride-types")
                        .header("Authorization", riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/ride-types")
                        .header("Authorization", driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/admin/ride-types rejected with 401 for unauthenticated request")
    void createRideType_Unauthenticated_Returns401() throws Exception {
        CreateRideTypeRequest request = CreateRideTypeRequest.builder()
                .name("Test Type")
                .code("TEST")
                .baseFare(new BigDecimal("50.00"))
                .perKmRate(new BigDecimal("15.00"))
                .perMinuteRate(new BigDecimal("2.50"))
                .minimumFare(new BigDecimal("80.00"))
                .capacity(4)
                .build();

        mockMvc.perform(post("/api/v1/admin/ride-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/admin/ride-types returns 409 Conflict on duplicate code")
    void createRideType_DuplicateCode_Returns409() throws Exception {
        CreateRideTypeRequest request = CreateRideTypeRequest.builder()
                .name("Another Go")
                .code("go") // Existing code is GO
                .baseFare(new BigDecimal("50.00"))
                .perKmRate(new BigDecimal("15.00"))
                .perMinuteRate(new BigDecimal("2.50"))
                .minimumFare(new BigDecimal("80.00"))
                .capacity(4)
                .build();

        mockMvc.perform(post("/api/v1/admin/ride-types")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("DUPLICATE_RESOURCE")));
    }

    @Test
    @DisplayName("POST /api/v1/admin/ride-types returns 400 Bad Request on invalid request validation")
    void createRideType_InvalidValidation_Returns400() throws Exception {
        CreateRideTypeRequest invalidRequest = CreateRideTypeRequest.builder()
                .name("") // Blank name
                .code("") // Blank code
                .baseFare(new BigDecimal("-10.00")) // Negative fare
                .capacity(0) // Invalid capacity (< 1)
                .build();

        mockMvc.perform(post("/api/v1/admin/ride-types")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("GET /api/v1/admin/ride-types returns all ride types including disabled")
    void getAllRideTypes_AsAdmin_ReturnsAll() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ride-types")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[2].code", is("PREMIER")))
                .andExpect(jsonPath("$[2].isActive", is(false)));
    }

    @Test
    @DisplayName("GET /api/v1/admin/ride-types/{uuid} returns full details for admin")
    void getRideTypeByUuid_AsAdmin_ReturnsDetails() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ride-types/" + disabledRideType.getUuid())
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid", is(disabledRideType.getUuid())))
                .andExpect(jsonPath("$.code", is("PREMIER")))
                .andExpect(jsonPath("$.isActive", is(false)));
    }

    @Test
    @DisplayName("GET /api/v1/admin/ride-types/{uuid} returns 404 for unknown UUID")
    void getRideTypeByUuid_UnknownUuid_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ride-types/missing-uuid-9999")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/ride-types/{uuid} updates permitted fields")
    void updateRideType_AsAdmin_Returns200() throws Exception {
        UpdateRideTypeRequest request = UpdateRideTypeRequest.builder()
                .name("LEXOR Go Express")
                .description("Super fast hatchback")
                .baseFare(new BigDecimal("55.00"))
                .perKmRate(new BigDecimal("16.00"))
                .perMinuteRate(new BigDecimal("2.60"))
                .minimumFare(new BigDecimal("85.00"))
                .cancellationFee(new BigDecimal("35.00"))
                .capacity(4)
                .iconUrl("https://cdn.lexor.mobility/icons/go-express.png")
                .build();

        mockMvc.perform(put("/api/v1/admin/ride-types/" + activeRideType.getUuid())
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("LEXOR Go Express")))
                .andExpect(jsonPath("$.baseFare", is(55.0)))
                .andExpect(jsonPath("$.minimumFare", is(85.0)));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/ride-types/{uuid} returns 400 when minFare < baseFare")
    void updateRideType_InvalidFareConstraint_Returns400() throws Exception {
        UpdateRideTypeRequest request = UpdateRideTypeRequest.builder()
                .name("LEXOR Go")
                .baseFare(new BigDecimal("100.00"))
                .perKmRate(new BigDecimal("15.00"))
                .perMinuteRate(new BigDecimal("2.50"))
                .minimumFare(new BigDecimal("60.00")) // Invalid: minFare < baseFare
                .capacity(4)
                .build();

        mockMvc.perform(put("/api/v1/admin/ride-types/" + activeRideType.getUuid())
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/ride-types/{uuid}/status enables and disables ride types")
    void updateRideTypeStatus_AsAdmin_Returns200() throws Exception {
        // Enable PREMIER
        UpdateRideTypeStatusRequest enableReq = UpdateRideTypeStatusRequest.builder()
                .isActive(true)
                .reason("Re-activating luxury tier")
                .build();

        mockMvc.perform(patch("/api/v1/admin/ride-types/" + disabledRideType.getUuid() + "/status")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enableReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive", is(true)));

        // Disable PREMIER again
        UpdateRideTypeStatusRequest disableReq = UpdateRideTypeStatusRequest.builder()
                .isActive(false)
                .reason("Temporary deactivation")
                .build();

        mockMvc.perform(patch("/api/v1/admin/ride-types/" + disabledRideType.getUuid() + "/status")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disableReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive", is(false)));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/ride-types/{uuid}/status returns 400 when attempting to disable last active category")
    void updateRideTypeStatus_DisablingLastActive_Returns400() throws Exception {
        // Disable secondActiveRideType (XL) first so only GO remains active
        rideTypeRepository.delete(secondActiveRideType);

        UpdateRideTypeStatusRequest disableReq = UpdateRideTypeStatusRequest.builder()
                .isActive(false)
                .reason("Deactivating last active")
                .build();

        mockMvc.perform(patch("/api/v1/admin/ride-types/" + activeRideType.getUuid() + "/status")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disableReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")));
    }

    @Test
    @DisplayName("DELETE /api/v1/admin/ride-types/{uuid} returns 400 to prevent accidental deletion of referenced records")
    void deleteRideType_RejectsDeletion_Returns400() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/ride-types/" + activeRideType.getUuid())
                        .header("Authorization", adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")));
    }

    @Test
    @DisplayName("DELETE /api/v1/admin/ride-types/{uuid} returns 403 for RIDER")
    void deleteRideType_AsRider_Returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/ride-types/" + activeRideType.getUuid())
                        .header("Authorization", riderToken))
                .andExpect(status().isForbidden());
    }
}
