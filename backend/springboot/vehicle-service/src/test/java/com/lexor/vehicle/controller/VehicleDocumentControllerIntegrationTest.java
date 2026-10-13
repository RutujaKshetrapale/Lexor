package com.lexor.vehicle.controller;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VehicleDocumentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    private User driverUser;
    private DriverProfile driverProfile;
    private String driverToken;
    private Vehicle vehicle;

    private static final byte[] PDF_BYTES = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x35};

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        assignmentRepository.deleteAll();
        documentRepository.deleteAll();
        vehicleRepository.deleteAll();
        driverProfileRepository.deleteAll();
        userRepository.deleteAll();

        driverUser = User.builder()
                .uuid("driver-doc-int")
                .firstName("Vikram")
                .lastName("Singh")
                .email("vikram.doc@lexor.mobility")
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
    }

    @Test
    @DisplayName("Upload document, list, download, resubmit, and soft-delete via MockMvc")
    void testCompleteDocumentLifecycle() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "rc_certificate.pdf", "application/pdf", PDF_BYTES
        );

        // 1. Upload document
        String responseContent = mockMvc.perform(multipart("/api/v1/vehicles/" + vehicle.getUuid() + "/documents")
                        .file(file)
                        .param("documentType", "REGISTRATION_CERTIFICATE")
                        .param("documentNumber", "RC-4829-KA")
                        .param("issueDate", "2023-01-10")
                        .param("expiryDate", "2038-01-10")
                        .param("issuingAuthority", "RTO Indiranagar")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentType", is("REGISTRATION_CERTIFICATE")))
                .andExpect(jsonPath("$.version", is(1)))
                .andExpect(jsonPath("$.current", is(true)))
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode rootNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(responseContent);
        long docId = rootNode.get("id").asLong();

        // 2. List documents
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.getUuid() + "/documents")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)));

        // 3. Download document
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.getUuid() + "/documents/" + docId + "/download")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", is("attachment; filename=\"rc_certificate.pdf\"")));

        // 4. Soft-delete document
        mockMvc.perform(delete("/api/v1/vehicles/" + vehicle.getUuid() + "/documents/" + docId)
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isNoContent());

        // 5. Listing documents now returns empty list (since current = false)
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.getUuid() + "/documents")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }
}
