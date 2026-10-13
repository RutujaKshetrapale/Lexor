package com.lexor.vehicle.service;

import com.lexor.vehicle.dto.request.AssignDriverRequest;
import com.lexor.vehicle.dto.request.ReviewVehicleDocumentRequest;
import com.lexor.vehicle.dto.request.ReviewVehicleRequest;
import com.lexor.vehicle.dto.request.SuspendVehicleRequest;
import com.lexor.vehicle.dto.response.DriverVehicleAssignmentResponse;
import com.lexor.vehicle.dto.response.PagedResponse;
import com.lexor.vehicle.dto.response.VehicleDocumentResponse;
import com.lexor.vehicle.dto.response.VehicleResponse;
import com.lexor.vehicle.dto.response.VehicleVerificationHistoryResponse;
import com.lexor.vehicle.entity.AssignmentStatus;
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
import com.lexor.vehicle.exception.InvalidStatusTransitionException;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class VehicleAdminServiceTest {

    @Autowired
    private VehicleAdminService adminService;

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

    private User adminUser;
    private UserPrincipal adminPrincipal;
    private User driverUser1;
    private User driverUser2;
    private DriverProfile driverProfile1;
    private DriverProfile driverProfile2;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        assignmentRepository.deleteAll();
        documentRepository.deleteAll();
        vehicleRepository.deleteAll();
        driverProfileRepository.deleteAll();
        userRepository.deleteAll();

        adminUser = User.builder()
                .uuid("admin-uuid-1")
                .firstName("Admin")
                .lastName("Lexor")
                .email("admin@lexor.mobility")
                .phoneNumber("+919876543299")
                .passwordHash("hashed")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        adminUser = userRepository.save(adminUser);
        adminPrincipal = UserPrincipal.create(adminUser);

        driverUser1 = User.builder()
                .uuid("driver-uuid-1")
                .firstName("Raj")
                .lastName("Kumar")
                .email("raj@lexor.mobility")
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

        driverUser2 = User.builder()
                .uuid("driver-uuid-2")
                .firstName("Sunil")
                .lastName("Verma")
                .email("sunil@lexor.mobility")
                .phoneNumber("+919876543202")
                .passwordHash("hashed")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .build();
        driverUser2 = userRepository.save(driverUser2);

        driverProfile2 = DriverProfile.builder()
                .user(driverUser2)
                .licenseNumber("KA-02-20230008888")
                .status(DriverStatus.OFFLINE)
                .onboardingStatus(OnboardingStatus.VERIFIED)
                .isVerified(true)
                .build();
        driverProfile2 = driverProfileRepository.save(driverProfile2);

        vehicle = Vehicle.builder()
                .uuid(UUID.randomUUID().toString())
                .driver(driverProfile1)
                .make("Toyota")
                .model("Camry")
                .year(2023)
                .color("Silver")
                .licensePlate("KA-01-EQ-9999")
                .rideTypeCategory("SEDAN")
                .seatCapacity(5)
                .fuelType(FuelType.HYBRID)
                .isActive(true)
                .verificationStatus(VehicleVerificationStatus.PENDING_VERIFICATION)
                .build();
        vehicle = vehicleRepository.save(vehicle);
    }

    @Test
    @DisplayName("Admin review individual document updates status and creates audit entry")
    void testReviewDocument() {
        VehicleDocument doc = VehicleDocument.builder()
                .vehicle(vehicle)
                .documentType(VehicleDocumentType.REGISTRATION_CERTIFICATE)
                .documentNumber("RC-9999")
                .fileName("rc.pdf")
                .filePath("target/test-uploads/rc.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .verificationStatus(DocumentVerificationStatus.PENDING_REVIEW)
                .version(1)
                .isCurrent(true)
                .build();
        doc = documentRepository.save(doc);

        ReviewVehicleDocumentRequest request = ReviewVehicleDocumentRequest.builder()
                .status(DocumentVerificationStatus.VERIFIED)
                .reviewNotes("Verified on government transport portal")
                .build();

        VehicleDocumentResponse response = adminService.reviewDocument(
                adminPrincipal, vehicle.getUuid(), doc.getId(), request
        );

        assertEquals(DocumentVerificationStatus.VERIFIED, response.getVerificationStatus());
        assertEquals(adminUser.getFullName(), response.getReviewedByAdminName());

        List<VehicleVerificationHistoryResponse> history = adminService.getVerificationHistory(vehicle.getUuid());
        assertFalse(history.isEmpty());
        assertEquals("DOCUMENT_REVIEW", history.get(0).getActionType());
        assertEquals("VERIFIED", history.get(0).getNewStatus());
    }

    @Test
    @DisplayName("Admin approve vehicle fails when mandatory documents are not verified")
    void testApproveVehicleFailsWithoutMandatoryDocuments() {
        ReviewVehicleRequest request = ReviewVehicleRequest.builder()
                .status(VehicleVerificationStatus.VERIFIED)
                .notes("Approving prematurely")
                .build();

        assertThrows(InvalidStatusTransitionException.class, () ->
                adminService.reviewVehicle(adminPrincipal, vehicle.getUuid(), request));
    }

    @Test
    @DisplayName("Admin approve vehicle succeeds when all mandatory documents are verified")
    void testApproveVehicleSucceedsWithAllDocumentsVerified() {
        // Create verified RC, Insurance, and PUC
        for (VehicleDocumentType type : List.of(
                VehicleDocumentType.REGISTRATION_CERTIFICATE,
                VehicleDocumentType.VEHICLE_INSURANCE,
                VehicleDocumentType.POLLUTION_UNDER_CONTROL
        )) {
            VehicleDocument doc = VehicleDocument.builder()
                    .vehicle(vehicle)
                    .documentType(type)
                    .documentNumber("DOC-" + type.name())
                    .fileName("doc.pdf")
                    .filePath("target/test-uploads/doc.pdf")
                    .contentType("application/pdf")
                    .fileSize(1024L)
                    .verificationStatus(DocumentVerificationStatus.VERIFIED)
                    .version(1)
                    .isCurrent(true)
                    .build();
            documentRepository.save(doc);
        }

        ReviewVehicleRequest request = ReviewVehicleRequest.builder()
                .status(VehicleVerificationStatus.VERIFIED)
                .notes("All documents thoroughly checked")
                .build();

        VehicleResponse response = adminService.reviewVehicle(adminPrincipal, vehicle.getUuid(), request);

        assertEquals(VehicleVerificationStatus.VERIFIED, response.getVerificationStatus());
        assertTrue(response.isInspected());
        assertNotNull(response.getVerifiedAt());
    }

    @Test
    @DisplayName("Admin reject vehicle requires rejection reason")
    void testRejectVehicleRequiresReason() {
        ReviewVehicleRequest request = ReviewVehicleRequest.builder()
                .status(VehicleVerificationStatus.REJECTED)
                .rejectionReason(null) // missing reason
                .build();

        assertThrows(BadRequestException.class, () ->
                adminService.reviewVehicle(adminPrincipal, vehicle.getUuid(), request));
    }

    @Test
    @DisplayName("Suspend vehicle deactivates active assignments and sets isActive false")
    void testSuspendVehicle() {
        // Set vehicle to verified
        vehicle.setVerificationStatus(VehicleVerificationStatus.VERIFIED);
        vehicle.setActive(true);
        vehicle = vehicleRepository.save(vehicle);

        // Create active assignment
        assignmentServiceTestSetup();

        SuspendVehicleRequest request = SuspendVehicleRequest.builder()
                .action("SUSPEND")
                .reason("Driver safety non-compliance investigation")
                .build();

        VehicleResponse response = adminService.suspendOrReinstateVehicle(adminPrincipal, vehicle.getUuid(), request);

        assertEquals(VehicleVerificationStatus.SUSPENDED, response.getVerificationStatus());
        assertFalse(response.isActive());

        // Reinstating
        SuspendVehicleRequest reinstateReq = SuspendVehicleRequest.builder()
                .action("REINSTATE")
                .reason("Investigation cleared; re-inspection passed")
                .build();

        VehicleResponse reinstated = adminService.suspendOrReinstateVehicle(adminPrincipal, vehicle.getUuid(), reinstateReq);
        assertEquals(VehicleVerificationStatus.VERIFIED, reinstated.getVerificationStatus());
        assertTrue(reinstated.isActive());
    }

    @Test
    @DisplayName("Assign driver enforces vehicle verification and updates assigned driver")
    void testAssignDriver() {
        // Vehicle must be verified and active
        vehicle.setVerificationStatus(VehicleVerificationStatus.VERIFIED);
        vehicle.setActive(true);
        vehicle = vehicleRepository.save(vehicle);

        AssignDriverRequest request = AssignDriverRequest.builder()
                .driverUuid(driverUser2.getUuid())
                .reason("Fleet vehicle reallocation to Sunil")
                .build();

        DriverVehicleAssignmentResponse response = adminService.assignDriver(
                adminPrincipal, vehicle.getUuid(), request
        );

        assertNotNull(response);
        assertEquals(driverUser2.getUuid(), response.getDriverUuid());
        assertEquals(AssignmentStatus.ACTIVE, response.getStatus());

        Vehicle updated = vehicleRepository.findByUuid(vehicle.getUuid()).orElseThrow();
        assertEquals(driverProfile2.getId(), updated.getDriver().getId());
    }

    @Test
    @DisplayName("Pagination and filtering for listVehicles")
    void testListVehiclesPaginationAndFiltering() {
        PagedResponse<VehicleResponse> response = adminService.listVehicles(
                null, null, null, null, PageRequest.of(0, 10)
        );

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals(1, response.getTotalElements());

        // Filter by non-matching search
        PagedResponse<VehicleResponse> emptyResp = adminService.listVehicles(
                null, null, null, "Ferrari", PageRequest.of(0, 10)
        );
        assertEquals(0, emptyResp.getContent().size());
    }

    private void assignmentServiceTestSetup() {
        com.lexor.vehicle.entity.DriverVehicleAssignment a = com.lexor.vehicle.entity.DriverVehicleAssignment.builder()
                .vehicle(vehicle)
                .driver(driverProfile1)
                .status(AssignmentStatus.ACTIVE)
                .assignedAt(java.time.LocalDateTime.now())
                .reason("Test assignment")
                .build();
        assignmentRepository.save(a);
    }
}
