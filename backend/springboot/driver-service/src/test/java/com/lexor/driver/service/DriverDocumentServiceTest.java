package com.lexor.driver.service;

import com.lexor.driver.dto.response.DocumentResponse;
import com.lexor.driver.entity.DocumentType;
import com.lexor.driver.entity.DocumentVerificationStatus;
import com.lexor.driver.entity.DriverDocument;
import com.lexor.driver.entity.DriverProfile;
import com.lexor.driver.entity.Role;
import com.lexor.driver.entity.User;
import com.lexor.driver.entity.UserStatus;
import com.lexor.driver.exception.BadRequestException;
import com.lexor.driver.exception.ForbiddenException;
import com.lexor.driver.repository.DriverDocumentRepository;
import com.lexor.driver.repository.DriverProfileRepository;
import com.lexor.driver.repository.DriverVerificationHistoryRepository;
import com.lexor.driver.repository.UserRepository;
import com.lexor.driver.security.UserPrincipal;
import com.lexor.driver.service.impl.DriverDocumentServiceImpl;
import com.lexor.driver.storage.DocumentStorageService;
import com.lexor.driver.storage.StoredDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverDocumentServiceTest {

    @Mock
    private DriverDocumentRepository documentRepository;

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DocumentStorageService storageService;

    @Mock
    private OnboardingPolicyService onboardingPolicyService;

    @Mock
    private DriverProfileService driverProfileService;

    @Mock
    private DriverVerificationHistoryRepository verificationHistoryRepository;

    @InjectMocks
    private DriverDocumentServiceImpl driverDocumentService;

    private User driverUser;
    private DriverProfile driverProfile;
    private UserPrincipal driverPrincipal;

    @BeforeEach
    void setUp() {
        driverUser = User.builder()
                .id(1L)
                .uuid("driver-uuid-1")
                .firstName("Vikram")
                .status(UserStatus.ACTIVE)
                .build();

        driverProfile = DriverProfile.builder()
                .id(10L)
                .user(driverUser)
                .build();

        driverPrincipal = new UserPrincipal(
                1L, "driver-uuid-1", "Vikram", "Singh", "v@ex.com", "pass",
                Role.DRIVER, UserStatus.ACTIVE,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_DRIVER")), true
        );
    }

    @Test
    @DisplayName("Should upload document with version 1 and PENDING_REVIEW status")
    void testUploadDocument_InitialVersion() {
        when(userRepository.findByUuid("driver-uuid-1")).thenReturn(Optional.of(driverUser));
        when(driverProfileService.getOrCreateDriverProfile(driverUser)).thenReturn(driverProfile);
        when(documentRepository.findByDriverIdAndDocumentTypeAndIsCurrentTrue(10L, DocumentType.DRIVING_LICENSE))
                .thenReturn(Optional.empty());

        StoredDocument stored = StoredDocument.builder()
                .originalFileName("dl.pdf")
                .storedFileName("stored-uuid.pdf")
                .storagePath("uploads/documents/stored-uuid.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .build();
        when(storageService.store(any())).thenReturn(stored);

        when(documentRepository.save(any(DriverDocument.class))).thenAnswer(i -> {
            DriverDocument doc = i.getArgument(0);
            doc.setId(100L);
            return doc;
        });

        MockMultipartFile file = new MockMultipartFile("file", "dl.pdf", "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46});

        DocumentResponse response = driverDocumentService.uploadDocument(
                driverPrincipal, DocumentType.DRIVING_LICENSE, "KA-01-DL1",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(3), "RTO", file
        );

        assertNotNull(response);
        assertEquals(1, response.getVersion());
        assertEquals(DocumentVerificationStatus.PENDING_REVIEW, response.getVerificationStatus());
        verify(documentRepository).save(any(DriverDocument.class));
    }

    @Test
    @DisplayName("Should supersede existing document on replacement and increment version")
    void testUploadDocument_ReplacementVersion() {
        DriverDocument oldDoc = DriverDocument.builder()
                .id(50L)
                .driver(driverProfile)
                .documentType(DocumentType.DRIVING_LICENSE)
                .version(1)
                .isCurrent(true)
                .verificationStatus(DocumentVerificationStatus.VERIFIED)
                .build();

        when(userRepository.findByUuid("driver-uuid-1")).thenReturn(Optional.of(driverUser));
        when(driverProfileService.getOrCreateDriverProfile(driverUser)).thenReturn(driverProfile);
        when(documentRepository.findByDriverIdAndDocumentTypeAndIsCurrentTrue(10L, DocumentType.DRIVING_LICENSE))
                .thenReturn(Optional.of(oldDoc));

        StoredDocument stored = StoredDocument.builder()
                .originalFileName("new_dl.pdf")
                .storagePath("uploads/documents/new.pdf")
                .contentType("application/pdf")
                .fileSize(2048L)
                .build();
        when(storageService.store(any())).thenReturn(stored);
        when(documentRepository.save(any(DriverDocument.class))).thenAnswer(i -> i.getArgument(0));

        MockMultipartFile file = new MockMultipartFile("file", "new_dl.pdf", "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46});

        DocumentResponse response = driverDocumentService.uploadDocument(
                driverPrincipal, DocumentType.DRIVING_LICENSE, "KA-01-DL2",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(4), "RTO", file
        );

        assertFalse(oldDoc.isCurrent());
        assertEquals(2, response.getVersion());
        assertEquals(DocumentVerificationStatus.PENDING_REVIEW, response.getVerificationStatus());
    }

    @Test
    @DisplayName("Should reject access when document belongs to another driver")
    void testGetDocumentById_ForbiddenToOtherDrivers() {
        DriverProfile otherDriver = DriverProfile.builder().id(999L).build();
        DriverDocument doc = DriverDocument.builder()
                .id(100L)
                .driver(otherDriver) // different driver!
                .build();

        when(userRepository.findByUuid("driver-uuid-1")).thenReturn(Optional.of(driverUser));
        when(driverProfileService.getOrCreateDriverProfile(driverUser)).thenReturn(driverProfile);
        when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));

        assertThrows(ForbiddenException.class, () -> driverDocumentService.getDriverDocumentById(driverPrincipal, 100L));
    }

    @Test
    @DisplayName("Should prevent driver from deleting a verified document")
    void testDeleteDocument_VerifiedDocumentCannotBeDeleted() {
        DriverDocument verifiedDoc = DriverDocument.builder()
                .id(100L)
                .driver(driverProfile)
                .verificationStatus(DocumentVerificationStatus.VERIFIED)
                .build();

        when(userRepository.findByUuid("driver-uuid-1")).thenReturn(Optional.of(driverUser));
        when(driverProfileService.getOrCreateDriverProfile(driverUser)).thenReturn(driverProfile);
        when(documentRepository.findById(100L)).thenReturn(Optional.of(verifiedDoc));

        assertThrows(BadRequestException.class, () -> driverDocumentService.deleteDocument(driverPrincipal, 100L));
    }
}
