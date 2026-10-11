package com.lexor.driver.service;

import com.lexor.driver.dto.request.DriverSuspensionRequest;
import com.lexor.driver.dto.request.ReviewDocumentRequest;
import com.lexor.driver.dto.request.VerifyDriverRequest;
import com.lexor.driver.dto.response.DocumentResponse;
import com.lexor.driver.dto.response.DriverProfileResponse;
import com.lexor.driver.entity.DocumentType;
import com.lexor.driver.entity.DocumentVerificationStatus;
import com.lexor.driver.entity.DriverDocument;
import com.lexor.driver.entity.DriverProfile;
import com.lexor.driver.entity.DriverStatus;
import com.lexor.driver.entity.DriverVerificationHistory;
import com.lexor.driver.entity.OnboardingStatus;
import com.lexor.driver.entity.Role;
import com.lexor.driver.entity.User;
import com.lexor.driver.entity.UserStatus;
import com.lexor.driver.exception.BadRequestException;
import com.lexor.driver.repository.DriverDocumentRepository;
import com.lexor.driver.repository.DriverProfileRepository;
import com.lexor.driver.repository.DriverVerificationHistoryRepository;
import com.lexor.driver.repository.UserRepository;
import com.lexor.driver.security.UserPrincipal;
import com.lexor.driver.service.impl.DriverVerificationServiceImpl;
import com.lexor.driver.storage.DocumentStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverVerificationServiceTest {

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private DriverDocumentRepository documentRepository;

    @Mock
    private DriverVerificationHistoryRepository verificationHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DocumentStorageService storageService;

    @Mock
    private DriverProfileService driverProfileService;

    @Mock
    private OnboardingPolicyService onboardingPolicyService;

    @InjectMocks
    private DriverVerificationServiceImpl verificationService;

    private User adminUser;
    private UserPrincipal adminPrincipal;
    private DriverProfile driverProfile;
    private User driverUser;
    private DriverDocument document;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(99L)
                .uuid("admin-uuid")
                .firstName("Rutuja")
                .lastName("Admin")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        adminPrincipal = new UserPrincipal(
                99L, "admin-uuid", "Rutuja", "Admin", "admin@lexor.mobility", "pass",
                Role.ADMIN, UserStatus.ACTIVE,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")), true
        );

        driverUser = User.builder()
                .id(1L)
                .uuid("driver-uuid")
                .firstName("Vikram")
                .lastName("Singh")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .build();

        driverProfile = DriverProfile.builder()
                .id(10L)
                .user(driverUser)
                .onboardingStatus(OnboardingStatus.PENDING_REVIEW)
                .status(DriverStatus.OFFLINE)
                .build();

        document = DriverDocument.builder()
                .id(100L)
                .driver(driverProfile)
                .documentType(DocumentType.DRIVING_LICENSE)
                .verificationStatus(DocumentVerificationStatus.PENDING_REVIEW)
                .fileName("dl.pdf")
                .isCurrent(true)
                .build();
    }

    @Test
    @DisplayName("Should approve document and record verification audit log")
    void testReviewDocument_Approved() {
        when(userRepository.findByUuid("admin-uuid")).thenReturn(Optional.of(adminUser));
        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(DriverDocument.class))).thenAnswer(i -> i.getArgument(0));

        ReviewDocumentRequest request = ReviewDocumentRequest.builder()
                .status(DocumentVerificationStatus.VERIFIED)
                .reviewNotes("All details match RTO records")
                .build();

        DocumentResponse response = verificationService.reviewDocument(adminPrincipal, 100L, request);

        assertEquals(DocumentVerificationStatus.VERIFIED, response.getVerificationStatus());
        assertEquals("All details match RTO records", response.getReviewNotes());
        verify(verificationHistoryRepository).save(any(DriverVerificationHistory.class));
    }

    @Test
    @DisplayName("Should reject document when rejection reason is missing")
    void testReviewDocument_MissingRejectionReason() {
        when(userRepository.findByUuid("admin-uuid")).thenReturn(Optional.of(adminUser));
        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));

        ReviewDocumentRequest request = ReviewDocumentRequest.builder()
                .status(DocumentVerificationStatus.REJECTED)
                .rejectionReason("") // blank reason!
                .build();

        assertThrows(BadRequestException.class, () -> verificationService.reviewDocument(adminPrincipal, 100L, request));
    }

    @Test
    @DisplayName("Should deny driver verification when documents are not yet verified")
    void testReviewDriverVerification_DeniedWhenDocsNotVerified() {
        when(userRepository.findByUuid("admin-uuid")).thenReturn(Optional.of(adminUser));
        when(driverProfileRepository.findByUserUuid("driver-uuid")).thenReturn(Optional.of(driverProfile));
        when(onboardingPolicyService.isProfileComplete(driverProfile)).thenReturn(true);
        when(onboardingPolicyService.areAllRequiredDocumentsVerified(10L)).thenReturn(false);

        VerifyDriverRequest request = VerifyDriverRequest.builder()
                .status(OnboardingStatus.VERIFIED)
                .notes("Approval attempt")
                .build();

        assertThrows(BadRequestException.class, () -> verificationService.reviewDriverVerification(adminPrincipal, "driver-uuid", request));
    }

    @Test
    @DisplayName("Should approve driver verification when all prerequisites are satisfied")
    void testReviewDriverVerification_Success() {
        when(userRepository.findByUuid("admin-uuid")).thenReturn(Optional.of(adminUser));
        when(driverProfileRepository.findByUserUuid("driver-uuid")).thenReturn(Optional.of(driverProfile));
        when(onboardingPolicyService.isProfileComplete(driverProfile)).thenReturn(true);
        when(onboardingPolicyService.areAllRequiredDocumentsVerified(10L)).thenReturn(true);
        when(driverProfileRepository.save(any(DriverProfile.class))).thenAnswer(i -> i.getArgument(0));

        DriverProfileResponse response = DriverProfileResponse.builder()
                .onboardingStatus(OnboardingStatus.VERIFIED)
                .isVerified(true)
                .build();
        when(driverProfileService.mapToResponse(any())).thenReturn(response);

        VerifyDriverRequest request = VerifyDriverRequest.builder()
                .status(OnboardingStatus.VERIFIED)
                .notes("Approved after complete verification")
                .build();

        DriverProfileResponse result = verificationService.reviewDriverVerification(adminPrincipal, "driver-uuid", request);

        assertTrue(result.isVerified());
        assertEquals(OnboardingStatus.VERIFIED, result.getOnboardingStatus());
        verify(verificationHistoryRepository).save(any(DriverVerificationHistory.class));
    }

    @Test
    @DisplayName("Should suspend driver and update availability to SUSPENDED")
    void testUpdateDriverSuspension_Suspend() {
        when(userRepository.findByUuid("admin-uuid")).thenReturn(Optional.of(adminUser));
        when(driverProfileRepository.findByUserUuid("driver-uuid")).thenReturn(Optional.of(driverProfile));
        when(driverProfileRepository.save(any(DriverProfile.class))).thenAnswer(i -> i.getArgument(0));

        DriverProfileResponse response = DriverProfileResponse.builder()
                .status(DriverStatus.SUSPENDED)
                .onboardingStatus(OnboardingStatus.SUSPENDED)
                .build();
        when(driverProfileService.mapToResponse(any())).thenReturn(response);

        DriverSuspensionRequest request = DriverSuspensionRequest.builder()
                .suspended(true)
                .reason("Compliance violation reported")
                .build();

        DriverProfileResponse result = verificationService.updateDriverSuspension(adminPrincipal, "driver-uuid", request);

        assertEquals(DriverStatus.SUSPENDED, result.getStatus());
        assertEquals(OnboardingStatus.SUSPENDED, result.getOnboardingStatus());
    }
}
