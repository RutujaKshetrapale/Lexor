package com.lexor.vehicle.service;

import com.lexor.vehicle.dto.response.VehicleDocumentResponse;
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
import com.lexor.vehicle.exception.BadRequestException;
import com.lexor.vehicle.repository.DriverProfileRepository;
import com.lexor.vehicle.repository.DriverVehicleAssignmentRepository;
import com.lexor.vehicle.repository.UserRepository;
import com.lexor.vehicle.repository.VehicleDocumentRepository;
import com.lexor.vehicle.repository.VehicleRepository;
import com.lexor.vehicle.repository.VehicleVerificationHistoryRepository;
import com.lexor.vehicle.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class VehicleDocumentServiceTest {

    @Autowired
    private VehicleDocumentService documentService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private VehicleDocumentRepository documentRepository;

    @Autowired
    private DriverProfileRepository driverProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DriverVehicleAssignmentRepository assignmentRepository;

    @Autowired
    private VehicleVerificationHistoryRepository historyRepository;

    private User driverUser;
    private DriverProfile driverProfile;
    private UserPrincipal driverPrincipal;
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
                .uuid("driver-doc-u")
                .firstName("Suresh")
                .lastName("Raina")
                .email("suresh@lexor.mobility")
                .phoneNumber("+919876543210")
                .passwordHash("hashed")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .build();
        driverUser = userRepository.save(driverUser);

        driverProfile = DriverProfile.builder()
                .user(driverUser)
                .licenseNumber("UP-14-20230009999")
                .status(DriverStatus.OFFLINE)
                .onboardingStatus(OnboardingStatus.VERIFIED)
                .isVerified(true)
                .build();
        driverProfile = driverProfileRepository.save(driverProfile);
        driverPrincipal = UserPrincipal.create(driverUser);

        vehicle = Vehicle.builder()
                .uuid(UUID.randomUUID().toString())
                .driver(driverProfile)
                .make("Hyundai")
                .model("Verna")
                .year(2023)
                .color("Fiery Red")
                .licensePlate("UP-14-EQ-9999")
                .rideTypeCategory("SEDAN")
                .seatCapacity(5)
                .fuelType(FuelType.PETROL)
                .isActive(true)
                .verificationStatus(VehicleVerificationStatus.PENDING_VERIFICATION)
                .build();
        vehicle = vehicleRepository.save(vehicle);
    }

    @Test
    @DisplayName("Successfully upload vehicle compliance document")
    void testUploadDocumentSuccess() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "rc.pdf", "application/pdf", PDF_BYTES
        );

        VehicleDocumentResponse response = documentService.uploadDocument(
                driverPrincipal, vehicle.getUuid(), VehicleDocumentType.REGISTRATION_CERTIFICATE,
                "RC-UP-14-9999", LocalDate.of(2023, 1, 1), LocalDate.of(2038, 1, 1),
                "RTO Ghaziabad", file
        );

        assertNotNull(response);
        assertEquals(VehicleDocumentType.REGISTRATION_CERTIFICATE, response.getDocumentType());
        assertEquals("RC-UP-14-9999", response.getDocumentNumber());
        assertEquals(1, response.getVersion());
        assertTrue(response.isCurrent());
        assertEquals(DocumentVerificationStatus.PENDING_REVIEW, response.getVerificationStatus());
    }

    @Test
    @DisplayName("Resubmitting a document bumps version and marks old version non-current")
    void testResubmitDocumentVersionBump() {
        MockMultipartFile file1 = new MockMultipartFile("file", "ins_v1.pdf", "application/pdf", PDF_BYTES);
        VehicleDocumentResponse v1 = documentService.uploadDocument(
                driverPrincipal, vehicle.getUuid(), VehicleDocumentType.VEHICLE_INSURANCE,
                "POL-1111", LocalDate.now(), LocalDate.now().plusYears(1), "ICICI Lombard", file1
        );

        // Mark v1 as rejected by admin
        VehicleDocument doc = documentRepository.findById(v1.getId()).orElseThrow();
        doc.setVerificationStatus(DocumentVerificationStatus.REJECTED);
        doc.setRejectionReason("Document image is partially cut off");
        documentRepository.save(doc);

        MockMultipartFile file2 = new MockMultipartFile("file", "ins_v2.pdf", "application/pdf", PDF_BYTES);
        VehicleDocumentResponse v2 = documentService.resubmitDocument(
                driverPrincipal, vehicle.getUuid(), v1.getId(),
                "POL-1111", LocalDate.now(), LocalDate.now().plusYears(1), "ICICI Lombard", file2
        );

        assertEquals(2, v2.getVersion());
        assertTrue(v2.isCurrent());
        assertEquals(DocumentVerificationStatus.PENDING_REVIEW, v2.getVerificationStatus());

        VehicleDocument oldDoc = documentRepository.findById(v1.getId()).orElseThrow();
        assertFalse(oldDoc.isCurrent());
    }

    @Test
    @DisplayName("Cannot resubmit an already verified document")
    void testCannotResubmitVerifiedDocument() {
        MockMultipartFile file = new MockMultipartFile("file", "puc.pdf", "application/pdf", PDF_BYTES);
        VehicleDocumentResponse response = documentService.uploadDocument(
                driverPrincipal, vehicle.getUuid(), VehicleDocumentType.POLLUTION_UNDER_CONTROL,
                "PUC-1234", LocalDate.now(), LocalDate.now().plusMonths(6), "PUC Center", file
        );

        VehicleDocument doc = documentRepository.findById(response.getId()).orElseThrow();
        doc.setVerificationStatus(DocumentVerificationStatus.VERIFIED);
        documentRepository.save(doc);

        MockMultipartFile newFile = new MockMultipartFile("file", "puc2.pdf", "application/pdf", PDF_BYTES);
        assertThrows(BadRequestException.class, () ->
                documentService.resubmitDocument(
                        driverPrincipal, vehicle.getUuid(), response.getId(),
                        "PUC-1234", LocalDate.now(), LocalDate.now().plusMonths(6), "PUC Center", newFile
                ));
    }

    @Test
    @DisplayName("Soft-delete an unverified document")
    void testSoftDeleteUnverifiedDocument() {
        MockMultipartFile file = new MockMultipartFile("file", "permit.pdf", "application/pdf", PDF_BYTES);
        VehicleDocumentResponse response = documentService.uploadDocument(
                driverPrincipal, vehicle.getUuid(), VehicleDocumentType.VEHICLE_PERMIT,
                "PERMIT-01", LocalDate.now(), LocalDate.now().plusYears(5), "Transport Dept", file
        );

        documentService.deleteDocument(driverPrincipal, vehicle.getUuid(), response.getId());

        VehicleDocument doc = documentRepository.findById(response.getId()).orElseThrow();
        assertFalse(doc.isCurrent());
    }

    @Test
    @DisplayName("Cannot delete an already verified document")
    void testCannotDeleteVerifiedDocument() {
        MockMultipartFile file = new MockMultipartFile("file", "rc.pdf", "application/pdf", PDF_BYTES);
        VehicleDocumentResponse response = documentService.uploadDocument(
                driverPrincipal, vehicle.getUuid(), VehicleDocumentType.REGISTRATION_CERTIFICATE,
                "RC-99", LocalDate.now(), LocalDate.now().plusYears(15), "RTO", file
        );

        VehicleDocument doc = documentRepository.findById(response.getId()).orElseThrow();
        doc.setVerificationStatus(DocumentVerificationStatus.VERIFIED);
        documentRepository.save(doc);

        assertThrows(BadRequestException.class, () ->
                documentService.deleteDocument(driverPrincipal, vehicle.getUuid(), response.getId()));
    }
}
