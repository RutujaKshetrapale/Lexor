package com.lexor.driver.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexor.driver.dto.EmergencyContactDto;
import com.lexor.driver.dto.request.DriverAvailabilityRequest;
import com.lexor.driver.dto.request.PatchDriverProfileRequest;
import com.lexor.driver.dto.request.UpdateDriverProfileRequest;
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

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DriverProfileControllerTest {

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

    private User driverUser;
    private User riderUser;
    private DriverProfile driverProfile;
    private String driverToken;
    private String riderToken;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        documentRepository.deleteAll();
        emergencyContactRepository.deleteAll();
        driverProfileRepository.deleteAll();
        userRepository.deleteAll();

        driverUser = User.builder()
                .uuid("driver-uuid-11111")
                .firstName("Vikram")
                .lastName("Singh")
                .email("vikram@lexor.mobility")
                .phoneNumber("+919876543212")
                .passwordHash("$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFj5/5l.K1N6r7F0WpE0yP1O2Q3R4S5T")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .phoneVerified(true)
                .build();
        driverUser = userRepository.save(driverUser);

        riderUser = User.builder()
                .uuid("rider-uuid-22222")
                .firstName("Rajesh")
                .lastName("Kumar")
                .email("rajesh@lexor.mobility")
                .phoneNumber("+919876543211")
                .passwordHash("$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFj5/5l.K1N6r7F0WpE0yP1O2Q3R4S5T")
                .role(Role.RIDER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .phoneVerified(true)
                .build();
        riderUser = userRepository.save(riderUser);

        driverProfile = DriverProfile.builder()
                .user(driverUser)
                .licenseNumber("KA-01-20230009876")
                .licenseExpiryDate(LocalDate.now().plusYears(3))
                .licenseIssueDate(LocalDate.now().minusYears(1))
                .dateOfBirth(LocalDate.of(1990, 5, 15))
                .residentialAddress("MG Road")
                .city("Bengaluru")
                .state("Karnataka")
                .postalCode("560038")
                .status(DriverStatus.OFFLINE)
                .onboardingStatus(OnboardingStatus.INCOMPLETE)
                .isVerified(false)
                .build();
        driverProfile = driverProfileRepository.save(driverProfile);

        driverToken = jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(driverUser));
        riderToken = jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(riderUser));
    }

    @Test
    @DisplayName("GET /api/v1/drivers/me without token returns 401 Unauthorized")
    void testGetProfile_Unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/drivers/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("GET /api/v1/drivers/me by RIDER returns 403 Forbidden")
    void testGetProfile_ForbiddenForRider() throws Exception {
        mockMvc.perform(get("/api/v1/drivers/me")
                        .header("Authorization", "Bearer " + riderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("GET /api/v1/drivers/me by authenticated DRIVER returns 200 OK")
    void testGetProfile_Success() throws Exception {
        mockMvc.perform(get("/api/v1/drivers/me")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseNumber", is("KA-01-20230009876")))
                .andExpect(jsonPath("$.firstName", is("Vikram")))
                .andExpect(jsonPath("$.onboardingStatus", is("INCOMPLETE")));
    }

    @Test
    @DisplayName("PUT /api/v1/drivers/me updates profile fields successfully")
    void testUpdateProfile_Success() throws Exception {
        UpdateDriverProfileRequest request = UpdateDriverProfileRequest.builder()
                .licenseNumber("KA-01-20250009999")
                .licenseIssueDate(LocalDate.now().minusYears(2))
                .licenseExpiryDate(LocalDate.now().plusYears(4))
                .dateOfBirth(LocalDate.of(1992, 6, 20))
                .residentialAddress("Indiranagar 100ft Road")
                .city("Bengaluru")
                .state("Karnataka")
                .postalCode("560038")
                .emergencyContact(EmergencyContactDto.builder()
                        .contactName("Priya Singh")
                        .contactPhone("+919876543299")
                        .relationship("Spouse")
                        .build())
                .build();

        mockMvc.perform(put("/api/v1/drivers/me")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseNumber", is("KA-01-20250009999")))
                .andExpect(jsonPath("$.emergencyContact.contactName", is("Priya Singh")));
    }

    @Test
    @DisplayName("PUT /api/v1/drivers/me with expired license date returns 400 Bad Request")
    void testUpdateProfile_ExpiredLicenseFails() throws Exception {
        UpdateDriverProfileRequest request = UpdateDriverProfileRequest.builder()
                .licenseNumber("KA-01-20200001111")
                .licenseIssueDate(LocalDate.now().minusYears(5))
                .licenseExpiryDate(LocalDate.now().minusDays(1)) // already expired!
                .dateOfBirth(LocalDate.of(1992, 6, 20))
                .residentialAddress("Indiranagar")
                .city("Bengaluru")
                .state("Karnataka")
                .postalCode("560038")
                .build();

        mockMvc.perform(put("/api/v1/drivers/me")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("PATCH /api/v1/drivers/me updates only supplied fields")
    void testPatchProfile_Success() throws Exception {
        PatchDriverProfileRequest request = PatchDriverProfileRequest.builder()
                .city("Mysuru")
                .build();

        mockMvc.perform(patch("/api/v1/drivers/me")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city", is("Mysuru")))
                .andExpect(jsonPath("$.licenseNumber", is("KA-01-20230009876"))); // preserved
    }

    @Test
    @DisplayName("GET /api/v1/drivers/me/onboarding returns checklist and completion status")
    void testGetOnboardingStatus_Success() throws Exception {
        mockMvc.perform(get("/api/v1/drivers/me/onboarding")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingStatus", is("INCOMPLETE")))
                .andExpect(jsonPath("$.documentChecklist", hasSize(greaterThan(0))));
    }

    @Test
    @DisplayName("POST /api/v1/drivers/me/submit-for-review returns 400 when prerequisites not met")
    void testSubmitForReview_IncompleteFails() throws Exception {
        mockMvc.perform(post("/api/v1/drivers/me/submit-for-review")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")));
    }

    @Test
    @DisplayName("PATCH /api/v1/drivers/me/availability returns 403 when unverified driver tries to go ONLINE")
    void testUpdateAvailability_UnverifiedFails() throws Exception {
        DriverAvailabilityRequest request = DriverAvailabilityRequest.builder()
                .status(DriverStatus.ONLINE)
                .build();

        mockMvc.perform(patch("/api/v1/drivers/me/availability")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("FORBIDDEN")));
    }
}
