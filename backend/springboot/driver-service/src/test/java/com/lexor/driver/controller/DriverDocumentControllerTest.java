package com.lexor.driver.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DriverDocumentControllerTest {

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

    private User driver1;
    private User driver2;
    private DriverProfile profile1;
    private DriverProfile profile2;
    private String driver1Token;
    private String driver2Token;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        documentRepository.deleteAll();
        emergencyContactRepository.deleteAll();
        driverProfileRepository.deleteAll();
        userRepository.deleteAll();

        driver1 = User.builder()
                .uuid("driver1-uuid")
                .firstName("Driver")
                .lastName("One")
                .email("driver1@lexor.mobility")
                .phoneNumber("+919876543210")
                .passwordHash("hash")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .build();
        driver1 = userRepository.save(driver1);

        driver2 = User.builder()
                .uuid("driver2-uuid")
                .firstName("Driver")
                .lastName("Two")
                .email("driver2@lexor.mobility")
                .phoneNumber("+919876543211")
                .passwordHash("hash")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .build();
        driver2 = userRepository.save(driver2);

        profile1 = DriverProfile.builder()
                .user(driver1)
                .licenseNumber("KA-01-D1")
                .status(DriverStatus.OFFLINE)
                .onboardingStatus(OnboardingStatus.INCOMPLETE)
                .build();
        profile1 = driverProfileRepository.save(profile1);

        profile2 = DriverProfile.builder()
                .user(driver2)
                .licenseNumber("KA-01-D2")
                .status(DriverStatus.OFFLINE)
                .onboardingStatus(OnboardingStatus.INCOMPLETE)
                .build();
        profile2 = driverProfileRepository.save(profile2);

        driver1Token = jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(driver1));
        driver2Token = jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(driver2));
    }

    @Test
    @DisplayName("POST /api/v1/drivers/me/documents uploads valid PDF document")
    void testUploadDocument_Success() throws Exception {
        byte[] pdfBytes = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};
        MockMultipartFile file = new MockMultipartFile("file", "license.pdf", "application/pdf", pdfBytes);

        mockMvc.perform(multipart("/api/v1/drivers/me/documents")
                        .file(file)
                        .param("documentType", "DRIVING_LICENSE")
                        .param("documentNumber", "KA-01-20230009876")
                        .param("issueDate", LocalDate.now().minusYears(1).toString())
                        .param("expiryDate", LocalDate.now().plusYears(3).toString())
                        .param("issuingAuthority", "Karnataka RTO")
                        .header("Authorization", "Bearer " + driver1Token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentType", is("DRIVING_LICENSE")))
                .andExpect(jsonPath("$.verificationStatus", is("PENDING_REVIEW")))
                .andExpect(jsonPath("$.version", is(1)));
    }

    @Test
    @DisplayName("POST /api/v1/drivers/me/documents rejects invalid file signature")
    void testUploadDocument_InvalidSignature() throws Exception {
        byte[] fakeBytes = "Not a PDF content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf", fakeBytes);

        mockMvc.perform(multipart("/api/v1/drivers/me/documents")
                        .file(file)
                        .param("documentType", "DRIVING_LICENSE")
                        .header("Authorization", "Bearer " + driver1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")));
    }

    @Test
    @DisplayName("GET /api/v1/drivers/me/documents/{documentId} forbids access to another driver's document")
    void testGetDocumentById_ForbiddenForOtherDriver() throws Exception {
        DriverDocument doc = DriverDocument.builder()
                .driver(profile1)
                .documentType(DocumentType.DRIVING_LICENSE)
                .fileName("dl.pdf")
                .filePath("target/test-uploads/dl.pdf")
                .contentType("application/pdf")
                .fileSize(100L)
                .verificationStatus(DocumentVerificationStatus.PENDING_REVIEW)
                .version(1)
                .isCurrent(true)
                .build();
        doc = documentRepository.save(doc);

        // Driver 2 attempts to view Driver 1's document:
        mockMvc.perform(get("/api/v1/drivers/me/documents/" + doc.getId())
                        .header("Authorization", "Bearer " + driver2Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("DELETE /api/v1/drivers/me/documents/{documentId} logically removes unverified document")
    void testDeleteDocument_Success() throws Exception {
        DriverDocument doc = DriverDocument.builder()
                .driver(profile1)
                .documentType(DocumentType.GOVERNMENT_ID)
                .fileName("id.pdf")
                .filePath("target/test-uploads/id.pdf")
                .contentType("application/pdf")
                .fileSize(100L)
                .verificationStatus(DocumentVerificationStatus.PENDING_REVIEW)
                .version(1)
                .isCurrent(true)
                .build();
        doc = documentRepository.save(doc);

        mockMvc.perform(delete("/api/v1/drivers/me/documents/" + doc.getId())
                        .header("Authorization", "Bearer " + driver1Token))
                .andExpect(status().isNoContent());

        DriverDocument reloaded = documentRepository.findById(doc.getId()).orElseThrow();
        assertFalse(reloaded.isCurrent());
    }
}
