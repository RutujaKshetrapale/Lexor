package com.lexor.vehicle.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexor.vehicle.dto.request.RegisterVehicleRequest;
import com.lexor.vehicle.dto.request.UpdateVehicleRequest;
import com.lexor.vehicle.dto.request.VehicleStatusUpdateRequest;
import com.lexor.vehicle.entity.DriverProfile;
import com.lexor.vehicle.entity.DriverStatus;
import com.lexor.vehicle.entity.FuelType;
import com.lexor.vehicle.entity.OnboardingStatus;
import com.lexor.vehicle.entity.Role;
import com.lexor.vehicle.entity.User;
import com.lexor.vehicle.entity.UserStatus;
import com.lexor.vehicle.entity.Vehicle;
import com.lexor.vehicle.entity.VehicleVerificationStatus;
import com.lexor.vehicle.repository.DriverProfileRepository;
import com.lexor.vehicle.repository.DriverVehicleAssignmentRepository;
import com.lexor.vehicle.repository.UserRepository;
import com.lexor.vehicle.repository.VehicleDocumentRepository;
import com.lexor.vehicle.repository.VehicleRepository;
import com.lexor.vehicle.repository.VehicleVerificationHistoryRepository;
import com.lexor.vehicle.security.JwtTokenProvider;
import com.lexor.vehicle.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DriverVehicleControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DriverProfileRepository driverProfileRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private VehicleDocumentRepository documentRepository;

    @Autowired
    private DriverVehicleAssignmentRepository assignmentRepository;

    @Autowired
    private VehicleVerificationHistoryRepository historyRepository;

    private User driverUser1;
    private User driverUser2;
    private User riderUser;
    private DriverProfile driverProfile1;
    private DriverProfile driverProfile2;
    private String driverToken1;
    private String driverToken2;
    private String riderToken;
    private Vehicle vehicle1;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        assignmentRepository.deleteAll();
        documentRepository.deleteAll();
        vehicleRepository.deleteAll();
        driverProfileRepository.deleteAll();
        userRepository.deleteAll();

        driverUser1 = User.builder()
                .uuid("driver-u-1")
                .firstName("Vikram")
                .lastName("Singh")
                .email("vikram@lexor.mobility")
                .phoneNumber("+919876543201")
                .passwordHash("hashed")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .build();
        driverUser1 = userRepository.save(driverUser1);

        driverProfile1 = DriverProfile.builder()
                .user(driverUser1)
                .licenseNumber("KA-01-20230009876")
                .status(DriverStatus.OFFLINE)
                .onboardingStatus(OnboardingStatus.VERIFIED)
                .isVerified(true)
                .build();
        driverProfile1 = driverProfileRepository.save(driverProfile1);
        driverToken1 = jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(driverUser1));

        driverUser2 = User.builder()
                .uuid("driver-u-2")
                .firstName("Amit")
                .lastName("Sharma")
                .email("amit@lexor.mobility")
                .phoneNumber("+919876543202")
                .passwordHash("hashed")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .build();
        driverUser2 = userRepository.save(driverUser2);

        driverProfile2 = DriverProfile.builder()
                .user(driverUser2)
                .licenseNumber("DL-04-20230001234")
                .status(DriverStatus.OFFLINE)
                .onboardingStatus(OnboardingStatus.VERIFIED)
                .isVerified(true)
                .build();
        driverProfile2 = driverProfileRepository.save(driverProfile2);
        driverToken2 = jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(driverUser2));

        riderUser = User.builder()
                .uuid("rider-u-1")
                .firstName("Rohan")
                .lastName("Mehta")
                .email("rohan@lexor.mobility")
                .phoneNumber("+919876543203")
                .passwordHash("hashed")
                .role(Role.RIDER)
                .status(UserStatus.ACTIVE)
                .build();
        riderUser = userRepository.save(riderUser);
        riderToken = jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(riderUser));

        vehicle1 = Vehicle.builder()
                .uuid(UUID.randomUUID().toString())
                .driver(driverProfile1)
                .make("Toyota")
                .model("Hyryder")
                .year(2023)
                .color("Midnight Black")
                .licensePlate("KA-01-EQ-4829")
                .rideTypeCategory("SEDAN")
                .seatCapacity(4)
                .fuelType(FuelType.HYBRID)
                .isActive(true)
                .verificationStatus(VehicleVerificationStatus.PENDING_VERIFICATION)
                .build();
        vehicle1 = vehicleRepository.save(vehicle1);
    }

    @Test
    @DisplayName("POST /api/v1/vehicles registers vehicle and returns 201")
    void testRegisterVehicleEndpoint() throws Exception {
        RegisterVehicleRequest request = RegisterVehicleRequest.builder()
                .make("Maruti Suzuki")
                .model("Ertiga")
                .year(2023)
                .color("Silver")
                .licensePlate("KA-03-MK-7890")
                .rideTypeCategory("XL")
                .seatCapacity(7)
                .fuelType(FuelType.CNG)
                .build();

        mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + driverToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.licensePlate", is("KA-03-MK-7890")))
                .andExpect(jsonPath("$.make", is("Maruti Suzuki")))
                .andExpect(jsonPath("$.verificationStatus", is("PENDING_VERIFICATION")));
    }

    @Test
    @DisplayName("GET /api/v1/vehicles lists authenticated driver's vehicles")
    void testGetMyVehiclesEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + driverToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].licensePlate", is("KA-01-EQ-4829")));
    }

    @Test
    @DisplayName("GET /api/v1/vehicles/{uuid} returns 403 for other driver's vehicle")
    void testCrossDriverAccessForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle1.getUuid())
                        .header("Authorization", "Bearer " + driverToken2))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RIDER role is forbidden (403) from accessing driver vehicle endpoint")
    void testRiderRoleForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + riderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request returns 401 Unauthorized")
    void testUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/v1/vehicles/{uuid} updates permitted vehicle details")
    void testUpdateVehicleEndpoint() throws Exception {
        UpdateVehicleRequest request = UpdateVehicleRequest.builder()
                .color("Pearl White")
                .seatCapacity(5)
                .build();

        mockMvc.perform(put("/api/v1/vehicles/" + vehicle1.getUuid())
                        .header("Authorization", "Bearer " + driverToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color", is("Pearl White")))
                .andExpect(jsonPath("$.seatCapacity", is(5)));
    }

    @Test
    @DisplayName("PATCH /api/v1/vehicles/{uuid}/status returns 400 when activating unverified vehicle")
    void testActivateUnverifiedVehicleReturnsBadRequest() throws Exception {
        VehicleStatusUpdateRequest request = VehicleStatusUpdateRequest.builder()
                .isActive(true)
                .build();

        mockMvc.perform(patch("/api/v1/vehicles/" + vehicle1.getUuid() + "/status")
                        .header("Authorization", "Bearer " + driverToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/vehicles/{uuid}/submit-verification returns 400 if documents missing")
    void testSubmitVerificationWithoutDocsReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/vehicles/" + vehicle1.getUuid() + "/submit-verification")
                        .header("Authorization", "Bearer " + driverToken1))
                .andExpect(status().isBadRequest());
    }
}
