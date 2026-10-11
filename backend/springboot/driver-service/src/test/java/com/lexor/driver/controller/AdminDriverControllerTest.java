package com.lexor.driver.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexor.driver.dto.request.DriverSuspensionRequest;
import com.lexor.driver.dto.request.ReviewDocumentRequest;
import com.lexor.driver.dto.request.VerifyDriverRequest;
import com.lexor.driver.entity.DocumentType;
import com.lexor.driver.entity.DocumentVerificationStatus;
import com.lexor.driver.entity.DriverDocument;
import com.lexor.driver.entity.DriverProfile;
import com.lexor.driver.entity.DriverStatus;
import com.lexor.driver.entity.OnboardingStatus;
import com.lexor.driver.entity.Role;
import com.lexor.driver.entity.User;
import com.lexor.driver.entity.UserStatus;
import com.lexor.driver.repository.DriverDocumentRepository;
import com.lexor.driver.repository.DriverProfileRepository;
import com.lexor.driver.repository.DriverVerificationHistoryRepository;
import com.lexor.driver.repository.EmergencyContactRepository;
import com.lexor.driver.repository.UserRepository;
import com.lexor.driver.security.JwtTokenProvider;
import com.lexor.driver.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDriverControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DriverProfileRepository driverProfileRepository;

    @Autowired
    private DriverDocumentRepository documentRepository;

    @Autowired
    private DriverVerificationHistoryRepository historyRepository;

    @Autowired
    private EmergencyContactRepository emergencyContactRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private User adminUser;
    private User driverUser;
    private DriverProfile driverProfile;
    private DriverDocument driverDocument;
    private String adminToken;
    private String driverToken;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        documentRepository.deleteAll();
        emergencyContactRepository.deleteAll();
        driverProfileRepository.deleteAll();
        userRepository.deleteAll();

        adminUser = User.builder()
                .uuid("admin-uuid-999")
                .firstName("Rutuja")
                .lastName("Admin")
                .email("admin@lexor.mobility")
                .phoneNumber("+919876543200")
                .passwordHash("hash")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        adminUser = userRepository.save(adminUser);

        driverUser = User.builder()
                .uuid("driver-uuid-888")
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
                .onboardingStatus(OnboardingStatus.PENDING_REVIEW)
                .isVerified(false)
                .build();
        driverProfile = driverProfileRepository.save(driverProfile);

        driverDocument = DriverDocument.builder()
                .driver(driverProfile)
                .documentType(DocumentType.DRIVING_LICENSE)
                .documentNumber("KA-01-20230009876")
                .fileName("dl.pdf")
                .filePath("target/test-uploads/dl.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .verificationStatus(DocumentVerificationStatus.PENDING_REVIEW)
                .version(1)
                .isCurrent(true)
                .build();
        driverDocument = documentRepository.save(driverDocument);

        adminToken = jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(adminUser));
        driverToken = jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(driverUser));
    }

    @Test
    @DisplayName("GET /api/v1/admin/drivers by DRIVER returns 403 Forbidden")
    void testListDrivers_ForbiddenForDriver() throws Exception {
        mockMvc.perform(get("/api/v1/admin/drivers")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("GET /api/v1/admin/drivers by ADMIN returns 200 OK with paginated list")
    void testListDrivers_AdminSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/admin/drivers")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].licenseNumber", is("KA-01-20230009876")));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/driver-documents/{id}/status approves document")
    void testReviewDocument_ApproveSuccess() throws Exception {
        ReviewDocumentRequest request = ReviewDocumentRequest.builder()
                .status(DocumentVerificationStatus.VERIFIED)
                .reviewNotes("Driving licence verified against government database")
                .build();

        mockMvc.perform(patch("/api/v1/admin/driver-documents/" + driverDocument.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus", is("VERIFIED")))
                .andExpect(jsonPath("$.reviewNotes", is("Driving licence verified against government database")));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/driver-documents/{id}/status rejects without reason returns 400")
    void testReviewDocument_RejectMissingReasonFails() throws Exception {
        ReviewDocumentRequest request = ReviewDocumentRequest.builder()
                .status(DocumentVerificationStatus.REJECTED)
                .rejectionReason("") // missing reason!
                .build();

        mockMvc.perform(patch("/api/v1/admin/driver-documents/" + driverDocument.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/drivers/{uuid}/verification-status returns 400 when documents are not verified")
    void testVerifyDriver_FailsWhenDocsNotVerified() throws Exception {
        VerifyDriverRequest request = VerifyDriverRequest.builder()
                .status(OnboardingStatus.VERIFIED)
                .notes("Attempting approval")
                .build();

        mockMvc.perform(patch("/api/v1/admin/drivers/" + driverUser.getUuid() + "/verification-status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/drivers/{uuid}/suspension suspends driver")
    void testSuspendDriver_Success() throws Exception {
        DriverSuspensionRequest request = DriverSuspensionRequest.builder()
                .suspended(true)
                .reason("Driver investigation in progress")
                .build();

        mockMvc.perform(patch("/api/v1/admin/drivers/" + driverUser.getUuid() + "/suspension")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUSPENDED")))
                .andExpect(jsonPath("$.onboardingStatus", is("SUSPENDED")));
    }

    @Test
    @DisplayName("GET /api/v1/admin/drivers/{uuid}/verification-history retrieves audit history")
    void testGetVerificationHistory_Success() throws Exception {
        mockMvc.perform(get("/api/v1/admin/drivers/" + driverUser.getUuid() + "/verification-history")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
