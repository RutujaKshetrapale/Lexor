package com.lexor.vehicle.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexor.vehicle.dto.request.AssignDriverRequest;
import com.lexor.vehicle.dto.request.ReviewVehicleDocumentRequest;
import com.lexor.vehicle.dto.request.ReviewVehicleRequest;
import com.lexor.vehicle.dto.request.SuspendVehicleRequest;
import com.lexor.vehicle.entity.DocumentVerificationStatus;
import com.lexor.vehicle.entity.DriverProfile;
import com.lexor.vehicle.entity.DriverStatus;
import com.lexor.vehicle.entity.FuelType;
import com.lexor.vehicle.entity.OnboardingStatus;
import com.lexor.vehicle.entity.Role;
import com.lexor.vehicle.entity.User;
import com.lexor.vehicle.entity.UserStatus;
import com.lexor.vehicle.entity.Vehicle;
import com.lexor.vehicle.entity.VehicleDocument;
import com.lexor.vehicle.entity.VehicleDocumentType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminVehicleControllerIntegrationTest {

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

    private User adminUser;
    private User driverUser;
    private DriverProfile driverProfile;
    private String adminToken;
    private String driverToken;
    private Vehicle vehicle;
    private VehicleDocument rcDoc;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        assignmentRepository.deleteAll();
        documentRepository.deleteAll();
        vehicleRepository.deleteAll();
        driverProfileRepository.deleteAll();
        userRepository.deleteAll();

        adminUser = User.builder()
                .uuid("admin-uuid-int")
                .firstName("Rutuja")
                .lastName("Admin")
                .email("admin@lexor.mobility")
                .phoneNumber("+919876543200")
                .passwordHash("hash")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        adminUser = userRepository.save(adminUser);
        adminToken = jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(adminUser));

        driverUser = User.builder()
                .uuid("driver-uuid-int")
                .firstName("Vikram")
                .lastName("Singh")
                .email("driver@lexor.mobility")
                .phoneNumber("+919876543201")
                .passwordHash("hash")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .build();
        driverUser = userRepository.save(driverUser);

        driverProfile = DriverProfile.builder()
                .user(driverUser)
                .licenseNumber("KA-01-20230009876")
                .status(DriverStatus.OFFLINE)
                .onboardingStatus(OnboardingStatus.VERIFIED)
                .isVerified(true)
                .build();
        driverProfile = driverProfileRepository.save(driverProfile);
        driverToken = jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(driverUser));

        vehicle = Vehicle.builder()
                .uuid(UUID.randomUUID().toString())
                .driver(driverProfile)
                .make("Toyota")
                .model("Hyryder")
                .year(2023)
                .color("Black")
                .licensePlate("KA-01-EQ-4829")
                .rideTypeCategory("SEDAN")
                .seatCapacity(4)
                .fuelType(FuelType.HYBRID)
                .isActive(true)
                .verificationStatus(VehicleVerificationStatus.PENDING_VERIFICATION)
                .build();
        vehicle = vehicleRepository.save(vehicle);

        rcDoc = VehicleDocument.builder()
                .vehicle(vehicle)
                .documentType(VehicleDocumentType.REGISTRATION_CERTIFICATE)
                .documentNumber("RC-4829")
                .fileName("rc.pdf")
                .filePath("target/test-uploads/rc.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .verificationStatus(DocumentVerificationStatus.PENDING_REVIEW)
                .version(1)
                .isCurrent(true)
                .build();
        rcDoc = documentRepository.save(rcDoc);
    }

    @Test
    @DisplayName("GET /api/v1/admin/vehicles lists vehicles with pagination")
    void testAdminListVehicles() throws Exception {
        mockMvc.perform(get("/api/v1/admin/vehicles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(1)))
                .andExpect(jsonPath("$.content[0].licensePlate", is("KA-01-EQ-4829")));
    }

    @Test
    @DisplayName("GET /api/v1/admin/vehicles returns 403 when called by a DRIVER")
    void testDriverForbiddenFromAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/admin/vehicles")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/vehicles/{uuid}/documents/{docId}/review reviews document")
    void testAdminReviewDocument() throws Exception {
        ReviewVehicleDocumentRequest request = ReviewVehicleDocumentRequest.builder()
                .status(DocumentVerificationStatus.VERIFIED)
                .reviewNotes("Verified against RTO registry")
                .build();

        mockMvc.perform(patch("/api/v1/admin/vehicles/" + vehicle.getUuid() + "/documents/" + rcDoc.getId() + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus", is("VERIFIED")));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/vehicles/{uuid}/suspension suspends vehicle")
    void testAdminSuspendVehicle() throws Exception {
        SuspendVehicleRequest request = SuspendVehicleRequest.builder()
                .action("SUSPEND")
                .reason("Emissions check failed")
                .build();

        mockMvc.perform(patch("/api/v1/admin/vehicles/" + vehicle.getUuid() + "/suspension")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus", is("SUSPENDED")))
                .andExpect(jsonPath("$.active", is(false)));
    }

    @Test
    @DisplayName("POST /api/v1/admin/vehicles/{uuid}/assignments assigns verified driver to verified vehicle")
    void testAdminAssignDriver() throws Exception {
        // Prepare verified vehicle
        vehicle.setVerificationStatus(VehicleVerificationStatus.VERIFIED);
        vehicle.setActive(true);
        vehicle = vehicleRepository.save(vehicle);

        AssignDriverRequest request = AssignDriverRequest.builder()
                .driverUuid(driverUser.getUuid())
                .reason("Driver assignment for urban fleet")
                .build();

        mockMvc.perform(post("/api/v1/admin/vehicles/" + vehicle.getUuid() + "/assignments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.driverUuid", is(driverUser.getUuid())));
    }
}
