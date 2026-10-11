package com.lexor.driver.service;

import com.lexor.driver.config.OnboardingPolicyConfig;
import com.lexor.driver.dto.response.DriverOnboardingResponse;
import com.lexor.driver.entity.DocumentType;
import com.lexor.driver.entity.DocumentVerificationStatus;
import com.lexor.driver.entity.DriverDocument;
import com.lexor.driver.entity.DriverProfile;
import com.lexor.driver.entity.EmergencyContact;
import com.lexor.driver.entity.OnboardingStatus;
import com.lexor.driver.entity.User;
import com.lexor.driver.repository.DriverDocumentRepository;
import com.lexor.driver.repository.EmergencyContactRepository;
import com.lexor.driver.service.impl.OnboardingPolicyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OnboardingPolicyServiceTest {

    @Mock
    private OnboardingPolicyConfig policyConfig;

    @Mock
    private DriverDocumentRepository documentRepository;

    @Mock
    private EmergencyContactRepository emergencyContactRepository;

    @InjectMocks
    private OnboardingPolicyServiceImpl onboardingPolicyService;

    private DriverProfile driver;
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .uuid("driver-uuid-1")
                .firstName("Test")
                .lastName("Driver")
                .build();

        driver = DriverProfile.builder()
                .id(10L)
                .user(user)
                .licenseNumber("DL-1234567890")
                .licenseExpiryDate(LocalDate.now().plusYears(3))
                .licenseIssueDate(LocalDate.now().minusYears(1))
                .dateOfBirth(LocalDate.now().minusYears(25))
                .residentialAddress("123 Main Street")
                .city("Bengaluru")
                .state("Karnataka")
                .postalCode("560001")
                .onboardingStatus(OnboardingStatus.INCOMPLETE)
                .build();

        lenient().when(policyConfig.getRequiredDocumentTypes()).thenReturn(
                Arrays.asList(DocumentType.DRIVING_LICENSE, DocumentType.GOVERNMENT_ID, DocumentType.ADDRESS_PROOF)
        );
    }

    @Test
    @DisplayName("Should detect incomplete profile when address is missing")
    void testIsProfileComplete_MissingAddress() {
        driver.setResidentialAddress(null);
        assertFalse(onboardingPolicyService.isProfileComplete(driver));
    }

    @Test
    @DisplayName("Should detect incomplete profile when driver is underage")
    void testIsProfileComplete_Underage() {
        driver.setDateOfBirth(LocalDate.now().minusYears(17));
        assertFalse(onboardingPolicyService.isProfileComplete(driver));
    }

    @Test
    @DisplayName("Should detect complete profile when all fields and emergency contact exist")
    void testIsProfileComplete_Success() {
        when(emergencyContactRepository.findFirstByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(EmergencyContact.builder().id(1L).build()));

        assertTrue(onboardingPolicyService.isProfileComplete(driver));
    }

    @Test
    @DisplayName("Should calculate onboarding progress when no documents uploaded")
    void testCalculateProgress_NoDocs() {
        when(documentRepository.findByDriverIdAndIsCurrentTrue(10L))
                .thenReturn(Collections.emptyList());
        when(emergencyContactRepository.findFirstByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        DriverOnboardingResponse response = onboardingPolicyService.calculateOnboardingProgress(driver);

        assertNotNull(response);
        assertEquals(OnboardingStatus.INCOMPLETE, response.getOnboardingStatus());
        assertFalse(response.isProfileComplete());
        assertFalse(response.isDocumentsComplete());
        assertFalse(response.isCanSubmitForReview());
        assertEquals(3, response.getDocumentChecklist().size());
        assertTrue(response.getOutstandingTasks().size() > 0);
    }

    @Test
    @DisplayName("Should allow submission for review when profile is complete and all docs uploaded")
    void testCalculateProgress_CanSubmitForReview() {
        when(emergencyContactRepository.findFirstByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(EmergencyContact.builder().id(1L).build()));

        List<DriverDocument> docs = Arrays.asList(
                DriverDocument.builder().id(1L).driver(driver).documentType(DocumentType.DRIVING_LICENSE).verificationStatus(DocumentVerificationStatus.PENDING_REVIEW).isCurrent(true).build(),
                DriverDocument.builder().id(2L).driver(driver).documentType(DocumentType.GOVERNMENT_ID).verificationStatus(DocumentVerificationStatus.PENDING_REVIEW).isCurrent(true).build(),
                DriverDocument.builder().id(3L).driver(driver).documentType(DocumentType.ADDRESS_PROOF).verificationStatus(DocumentVerificationStatus.PENDING_REVIEW).isCurrent(true).build()
        );
        when(documentRepository.findByDriverIdAndIsCurrentTrue(10L)).thenReturn(docs);

        DriverOnboardingResponse response = onboardingPolicyService.calculateOnboardingProgress(driver);

        assertTrue(response.isProfileComplete());
        assertTrue(response.isCanSubmitForReview());
        assertEquals(100, response.getCompletionPercentage());
    }

    @Test
    @DisplayName("Should set onboarding status to REJECTED when any required document is rejected")
    void testEvaluateAndSync_RejectedDoc() {
        List<DriverDocument> docs = Arrays.asList(
                DriverDocument.builder().id(1L).driver(driver).documentType(DocumentType.DRIVING_LICENSE).verificationStatus(DocumentVerificationStatus.REJECTED).rejectionReason("Blurry photo").isCurrent(true).build()
        );
        when(documentRepository.findByDriverIdAndIsCurrentTrue(10L)).thenReturn(docs);

        onboardingPolicyService.evaluateAndSyncDriverOnboardingStatus(driver);

        assertEquals(OnboardingStatus.REJECTED, driver.getOnboardingStatus());
        assertFalse(driver.isVerified());
    }
}
