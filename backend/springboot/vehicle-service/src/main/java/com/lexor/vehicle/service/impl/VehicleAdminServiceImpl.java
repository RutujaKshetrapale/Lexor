package com.lexor.vehicle.service.impl;

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
import com.lexor.vehicle.entity.DriverVehicleAssignment;
import com.lexor.vehicle.entity.OnboardingStatus;
import com.lexor.vehicle.entity.User;
import com.lexor.vehicle.entity.Vehicle;
import com.lexor.vehicle.entity.VehicleDocument;
import com.lexor.vehicle.entity.VehicleDocumentType;
import com.lexor.vehicle.entity.VehicleVerificationHistory;
import com.lexor.vehicle.entity.VehicleVerificationStatus;
import com.lexor.vehicle.exception.BadRequestException;
import com.lexor.vehicle.exception.InvalidStatusTransitionException;
import com.lexor.vehicle.exception.ResourceNotFoundException;
import com.lexor.vehicle.repository.DriverProfileRepository;
import com.lexor.vehicle.repository.DriverVehicleAssignmentRepository;
import com.lexor.vehicle.repository.UserRepository;
import com.lexor.vehicle.repository.VehicleDocumentRepository;
import com.lexor.vehicle.repository.VehicleRepository;
import com.lexor.vehicle.repository.VehicleVerificationHistoryRepository;
import com.lexor.vehicle.security.UserPrincipal;
import com.lexor.vehicle.service.VehicleAdminService;
import com.lexor.vehicle.service.VehicleVerificationPolicyService;
import com.lexor.vehicle.storage.DocumentStorageService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VehicleAdminServiceImpl implements VehicleAdminService {

    private static final Logger logger = LoggerFactory.getLogger(VehicleAdminServiceImpl.class);

    private final VehicleRepository vehicleRepository;
    private final VehicleDocumentRepository documentRepository;
    private final VehicleVerificationHistoryRepository historyRepository;
    private final DriverVehicleAssignmentRepository assignmentRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final UserRepository userRepository;
    private final DocumentStorageService storageService;
    private final VehicleVerificationPolicyService policyService;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<VehicleResponse> listVehicles(
            VehicleVerificationStatus verificationStatus,
            Boolean isActive,
            String rideTypeCategory,
            String search,
            Pageable pageable
    ) {
        Specification<Vehicle> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (verificationStatus != null) {
                predicates.add(cb.equal(root.get("verificationStatus"), verificationStatus));
            }
            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), isActive));
            }
            if (StringUtils.hasText(rideTypeCategory)) {
                predicates.add(cb.equal(cb.upper(root.get("rideTypeCategory")), rideTypeCategory.trim().toUpperCase()));
            }
            if (StringUtils.hasText(search)) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Predicate makeMatch = cb.like(cb.lower(root.get("make")), pattern);
                Predicate modelMatch = cb.like(cb.lower(root.get("model")), pattern);
                Predicate plateMatch = cb.like(cb.lower(root.get("licensePlate")), pattern);
                predicates.add(cb.or(makeMatch, modelMatch, plateMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Vehicle> page = vehicleRepository.findAll(spec, pageable);
        List<VehicleResponse> content = page.getContent().stream()
                .map(this::mapToVehicleResponse)
                .collect(Collectors.toList());

        return PagedResponse.<VehicleResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<VehicleResponse> listPendingVehicles(Pageable pageable) {
        Page<Vehicle> page = vehicleRepository.findByVerificationStatus(VehicleVerificationStatus.PENDING_VERIFICATION, pageable);
        List<VehicleResponse> content = page.getContent().stream()
                .map(this::mapToVehicleResponse)
                .collect(Collectors.toList());

        return PagedResponse.<VehicleResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleResponse getVehicleDetails(String vehicleUuid) {
        Vehicle vehicle = getVehicle(vehicleUuid);
        return mapToVehicleResponse(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleDocumentResponse> getVehicleDocuments(String vehicleUuid) {
        Vehicle vehicle = getVehicle(vehicleUuid);
        List<VehicleDocument> documents = documentRepository.findByVehicleIdAndIsCurrentTrue(vehicle.getId());
        return documents.stream().map(this::mapToDocumentResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadDocument(String vehicleUuid, Long documentId) {
        Vehicle vehicle = getVehicle(vehicleUuid);
        VehicleDocument document = documentRepository.findByIdAndVehicleId(documentId, vehicle.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        return storageService.loadAsResource(document.getFilePath());
    }

    @Override
    @Transactional
    public VehicleDocumentResponse reviewDocument(
            UserPrincipal adminPrincipal,
            String vehicleUuid,
            Long documentId,
            ReviewVehicleDocumentRequest request
    ) {
        Vehicle vehicle = getVehicle(vehicleUuid);
        VehicleDocument document = documentRepository.findByIdAndVehicleId(documentId, vehicle.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        User adminUser = userRepository.findById(adminPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found"));

        DocumentVerificationStatus oldStatus = document.getVerificationStatus();
        document.setVerificationStatus(request.getStatus());
        document.setRejectionReason(request.getRejectionReason());
        document.setReviewNotes(request.getReviewNotes());
        document.setReviewedByAdmin(adminUser);
        document.setReviewedAt(LocalDateTime.now());
        document = documentRepository.save(document);

        // Record verification audit history
        VehicleVerificationHistory history = VehicleVerificationHistory.builder()
                .vehicle(vehicle)
                .document(document)
                .actionType("DOCUMENT_REVIEW")
                .previousStatus(oldStatus.name())
                .newStatus(request.getStatus().name())
                .reviewedByAdmin(adminUser)
                .reason(StringUtils.hasText(request.getRejectionReason()) ? request.getRejectionReason() : request.getReviewNotes())
                .build();
        historyRepository.save(history);

        logger.info("Admin {} reviewed document ID {} on vehicle {}: {} -> {}",
                adminUser.getEmail(), documentId, vehicleUuid, oldStatus, request.getStatus());

        return mapToDocumentResponse(document);
    }

    @Override
    @Transactional
    public VehicleResponse reviewVehicle(
            UserPrincipal adminPrincipal,
            String vehicleUuid,
            ReviewVehicleRequest request
    ) {
        Vehicle vehicle = getVehicle(vehicleUuid);
        User adminUser = userRepository.findById(adminPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found"));

        VehicleVerificationStatus oldStatus = vehicle.getVerificationStatus();

        if (request.getStatus() == VehicleVerificationStatus.VERIFIED) {
            // Verify mandatory compliance documents
            List<VehicleDocument> currentDocs = documentRepository.findByVehicleIdAndIsCurrentTrue(vehicle.getId());
            boolean allVerified = policyService.hasAllRequiredDocumentsVerified(currentDocs);
            if (!allVerified) {
                List<VehicleDocumentType> missing = policyService.getMissingDocuments(currentDocs);
                throw new InvalidStatusTransitionException(
                        "Cannot approve vehicle. Mandatory compliance documents are not fully verified. Missing or unverified: " +
                                (missing.isEmpty() ? "one or more documents still pending review" : missing)
                );
            }

            vehicle.setVerificationStatus(VehicleVerificationStatus.VERIFIED);
            vehicle.setInspected(true);
            vehicle.setVerifiedAt(LocalDateTime.now());
            vehicle.setReviewedByAdmin(adminUser);
            vehicle.setRejectionReason(null);
        } else if (request.getStatus() == VehicleVerificationStatus.REJECTED) {
            if (!StringUtils.hasText(request.getRejectionReason())) {
                throw new BadRequestException("Rejection reason is mandatory when rejecting a vehicle");
            }
            vehicle.setVerificationStatus(VehicleVerificationStatus.REJECTED);
            vehicle.setInspected(false);
            vehicle.setRejectionReason(request.getRejectionReason().trim());
            vehicle.setReviewedByAdmin(adminUser);
        } else {
            throw new BadRequestException("Review decision status must be VERIFIED or REJECTED");
        }

        vehicle = vehicleRepository.save(vehicle);

        // Record verification audit history
        VehicleVerificationHistory history = VehicleVerificationHistory.builder()
                .vehicle(vehicle)
                .actionType("VEHICLE_VERIFICATION")
                .previousStatus(oldStatus.name())
                .newStatus(request.getStatus().name())
                .reviewedByAdmin(adminUser)
                .reason(StringUtils.hasText(request.getRejectionReason()) ? request.getRejectionReason() : request.getNotes())
                .build();
        historyRepository.save(history);

        logger.info("Admin {} changed vehicle {} status from {} to {}",
                adminUser.getEmail(), vehicleUuid, oldStatus, request.getStatus());

        return mapToVehicleResponse(vehicle);
    }

    @Override
    @Transactional
    public VehicleResponse suspendOrReinstateVehicle(
            UserPrincipal adminPrincipal,
            String vehicleUuid,
            SuspendVehicleRequest request
    ) {
        Vehicle vehicle = getVehicle(vehicleUuid);
        User adminUser = userRepository.findById(adminPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found"));

        VehicleVerificationStatus oldStatus = vehicle.getVerificationStatus();
        String action = request.getAction().trim().toUpperCase();

        if ("SUSPEND".equals(action)) {
            vehicle.setVerificationStatus(VehicleVerificationStatus.SUSPENDED);
            vehicle.setActive(false);
            vehicle.setRejectionReason(request.getReason());
            vehicle.setReviewedByAdmin(adminUser);

            // Deactivate active assignments
            Optional<DriverVehicleAssignment> activeAssignment = assignmentRepository.findByVehicleIdAndStatus(
                    vehicle.getId(), AssignmentStatus.ACTIVE
            );
            if (activeAssignment.isPresent()) {
                DriverVehicleAssignment a = activeAssignment.get();
                a.setStatus(AssignmentStatus.INACTIVE);
                a.setUnassignedAt(LocalDateTime.now());
                a.setReason("Vehicle suspended by admin: " + request.getReason());
                assignmentRepository.save(a);
            }

            VehicleVerificationHistory history = VehicleVerificationHistory.builder()
                    .vehicle(vehicle)
                    .actionType("VEHICLE_SUSPENSION")
                    .previousStatus(oldStatus.name())
                    .newStatus(VehicleVerificationStatus.SUSPENDED.name())
                    .reviewedByAdmin(adminUser)
                    .reason(request.getReason())
                    .build();
            historyRepository.save(history);

        } else if ("REINSTATE".equals(action)) {
            if (oldStatus != VehicleVerificationStatus.SUSPENDED) {
                throw new InvalidStatusTransitionException("Only a suspended vehicle can be reinstated");
            }

            // Verify mandatory compliance documents before reinstatement
            List<VehicleDocument> currentDocs = documentRepository.findByVehicleIdAndIsCurrentTrue(vehicle.getId());
            boolean allVerified = policyService.hasAllRequiredDocumentsVerified(currentDocs);
            if (!allVerified) {
                List<VehicleDocumentType> missing = policyService.getMissingDocuments(currentDocs);
                throw new InvalidStatusTransitionException(
                        "Cannot reinstate vehicle. Mandatory compliance documents are not fully verified. Missing or unverified: " +
                                (missing.isEmpty() ? "one or more documents still pending review" : missing)
                );
            }

            vehicle.setVerificationStatus(VehicleVerificationStatus.VERIFIED);
            vehicle.setActive(true);
            vehicle.setRejectionReason(null);
            vehicle.setReviewedByAdmin(adminUser);

            VehicleVerificationHistory history = VehicleVerificationHistory.builder()
                    .vehicle(vehicle)
                    .actionType("VEHICLE_REINSTATEMENT")
                    .previousStatus(oldStatus.name())
                    .newStatus(VehicleVerificationStatus.VERIFIED.name())
                    .reviewedByAdmin(adminUser)
                    .reason(request.getReason())
                    .build();
            historyRepository.save(history);

        } else {
            throw new BadRequestException("Action must be either 'SUSPEND' or 'REINSTATE'");
        }

        vehicle = vehicleRepository.save(vehicle);
        logger.info("Admin {} performed {} on vehicle {}", adminUser.getEmail(), action, vehicleUuid);
        return mapToVehicleResponse(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleVerificationHistoryResponse> getVerificationHistory(String vehicleUuid) {
        Vehicle vehicle = getVehicle(vehicleUuid);
        List<VehicleVerificationHistory> history = historyRepository.findByVehicleIdOrderByCreatedAtDesc(vehicle.getId());
        return history.stream().map(this::mapToHistoryResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DriverVehicleAssignmentResponse assignDriver(
            UserPrincipal adminPrincipal,
            String vehicleUuid,
            AssignDriverRequest request
    ) {
        Vehicle vehicle = getVehicle(vehicleUuid);
        User adminUser = userRepository.findById(adminPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found"));

        // Business rule 6 & 7: Only verified and active vehicles can be assigned
        if (vehicle.getVerificationStatus() != VehicleVerificationStatus.VERIFIED || !vehicle.isActive()) {
            throw new InvalidStatusTransitionException("Vehicle " + vehicle.getLicensePlate() + " is not verified or is inactive. Only verified active vehicles can be assigned.");
        }

        // Look up driver profile
        DriverProfile driver = driverProfileRepository.findByUserUuid(request.getDriverUuid())
                .orElseThrow(() -> new ResourceNotFoundException("Driver profile not found with user UUID: " + request.getDriverUuid()));

        // Business rule 7: Enforce driver eligibility
        if (!driver.isVerified() || driver.getOnboardingStatus() != OnboardingStatus.VERIFIED) {
            throw new InvalidStatusTransitionException("Driver " + driver.getUser().getFullName() + " is not verified for vehicle assignment");
        }
        if (driver.getStatus() == DriverStatus.SUSPENDED) {
            throw new InvalidStatusTransitionException("Cannot assign vehicle to a suspended driver");
        }

        // Deactivate existing active assignment for this vehicle if present
        Optional<DriverVehicleAssignment> currentAssignmentOpt = assignmentRepository.findByVehicleIdAndStatus(
                vehicle.getId(), AssignmentStatus.ACTIVE
        );
        if (currentAssignmentOpt.isPresent()) {
            DriverVehicleAssignment current = currentAssignmentOpt.get();
            current.setStatus(AssignmentStatus.TERMINATED);
            current.setUnassignedAt(LocalDateTime.now());
            current.setReason("Replaced by administrative assignment to driver " + driver.getUser().getFullName());
            assignmentRepository.save(current);
        }

        // Assign to new driver
        DriverVehicleAssignment newAssignment = DriverVehicleAssignment.builder()
                .vehicle(vehicle)
                .driver(driver)
                .assignedByAdmin(adminUser)
                .status(AssignmentStatus.ACTIVE)
                .assignedAt(LocalDateTime.now())
                .reason(request.getReason())
                .build();
        newAssignment = assignmentRepository.save(newAssignment);

        // Update current assigned driver on vehicle
        vehicle.setDriver(driver);
        vehicleRepository.save(vehicle);

        logger.info("Admin {} assigned vehicle {} to driver {}", adminUser.getEmail(), vehicleUuid, driver.getUser().getEmail());
        return mapToAssignmentResponse(newAssignment);
    }

    @Override
    @Transactional
    public DriverVehicleAssignmentResponse unassignDriver(
            UserPrincipal adminPrincipal,
            String vehicleUuid,
            Long assignmentId,
            String reason
    ) {
        Vehicle vehicle = getVehicle(vehicleUuid);
        DriverVehicleAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found with ID: " + assignmentId));

        if (!assignment.getVehicle().getId().equals(vehicle.getId())) {
            throw new BadRequestException("Assignment does not belong to specified vehicle");
        }

        assignment.setStatus(AssignmentStatus.TERMINATED);
        assignment.setUnassignedAt(LocalDateTime.now());
        assignment.setReason(reason != null ? reason : "Unassigned by administrator");
        assignment = assignmentRepository.save(assignment);

        logger.info("Unassigned driver assignment ID {} for vehicle {}", assignmentId, vehicleUuid);
        return mapToAssignmentResponse(assignment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DriverVehicleAssignmentResponse> getAssignmentHistory(String vehicleUuid) {
        Vehicle vehicle = getVehicle(vehicleUuid);
        List<DriverVehicleAssignment> assignments = assignmentRepository.findByVehicleIdOrderByAssignedAtDesc(vehicle.getId());
        return assignments.stream().map(this::mapToAssignmentResponse).collect(Collectors.toList());
    }

    private Vehicle getVehicle(String vehicleUuid) {
        return vehicleRepository.findByUuid(vehicleUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with UUID: " + vehicleUuid));
    }

    private VehicleResponse mapToVehicleResponse(Vehicle vehicle) {
        return VehicleResponse.builder()
                .uuid(vehicle.getUuid())
                .driverUuid(vehicle.getDriver() != null && vehicle.getDriver().getUser() != null ? vehicle.getDriver().getUser().getUuid() : null)
                .driverName(vehicle.getDriver() != null && vehicle.getDriver().getUser() != null ? vehicle.getDriver().getUser().getFullName() : null)
                .make(vehicle.getMake())
                .model(vehicle.getModel())
                .year(vehicle.getYear())
                .color(vehicle.getColor())
                .licensePlate(vehicle.getLicensePlate())
                .vin(vehicle.getVin())
                .rideTypeCategory(vehicle.getRideTypeCategory())
                .seatCapacity(vehicle.getSeatCapacity())
                .fuelType(vehicle.getFuelType())
                .isActive(vehicle.isActive())
                .isInspected(vehicle.isInspected())
                .inspectionExpiresAt(vehicle.getInspectionExpiresAt())
                .verificationStatus(vehicle.getVerificationStatus())
                .rejectionReason(vehicle.getRejectionReason())
                .verifiedAt(vehicle.getVerifiedAt())
                .createdAt(vehicle.getCreatedAt())
                .updatedAt(vehicle.getUpdatedAt())
                .build();
    }

    private VehicleDocumentResponse mapToDocumentResponse(VehicleDocument doc) {
        return VehicleDocumentResponse.builder()
                .id(doc.getId())
                .vehicleUuid(doc.getVehicle().getUuid())
                .documentType(doc.getDocumentType())
                .documentNumber(doc.getDocumentNumber())
                .fileName(doc.getFileName())
                .contentType(doc.getContentType())
                .fileSize(doc.getFileSize())
                .issueDate(doc.getIssueDate())
                .expiryDate(doc.getExpiryDate())
                .issuingAuthority(doc.getIssuingAuthority())
                .verificationStatus(doc.getVerificationStatus())
                .rejectionReason(doc.getRejectionReason())
                .reviewNotes(doc.getReviewNotes())
                .reviewedByAdminName(doc.getReviewedByAdmin() != null ? doc.getReviewedByAdmin().getFullName() : null)
                .reviewedAt(doc.getReviewedAt())
                .version(doc.getVersion())
                .isCurrent(doc.isCurrent())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    private VehicleVerificationHistoryResponse mapToHistoryResponse(VehicleVerificationHistory history) {
        return VehicleVerificationHistoryResponse.builder()
                .id(history.getId())
                .vehicleUuid(history.getVehicle().getUuid())
                .documentId(history.getDocument() != null ? history.getDocument().getId() : null)
                .documentType(history.getDocument() != null ? history.getDocument().getDocumentType() : null)
                .actionType(history.getActionType())
                .previousStatus(history.getPreviousStatus())
                .newStatus(history.getNewStatus())
                .reviewedByAdminName(history.getReviewedByAdmin() != null ? history.getReviewedByAdmin().getFullName() : null)
                .reason(history.getReason())
                .createdAt(history.getCreatedAt())
                .build();
    }

    private DriverVehicleAssignmentResponse mapToAssignmentResponse(DriverVehicleAssignment assignment) {
        return DriverVehicleAssignmentResponse.builder()
                .id(assignment.getId())
                .vehicleUuid(assignment.getVehicle().getUuid())
                .vehiclePlate(assignment.getVehicle().getLicensePlate())
                .driverUuid(assignment.getDriver() != null && assignment.getDriver().getUser() != null ? assignment.getDriver().getUser().getUuid() : null)
                .driverName(assignment.getDriver() != null && assignment.getDriver().getUser() != null ? assignment.getDriver().getUser().getFullName() : null)
                .assignedByAdminName(assignment.getAssignedByAdmin() != null ? assignment.getAssignedByAdmin().getFullName() : null)
                .status(assignment.getStatus())
                .assignedAt(assignment.getAssignedAt())
                .unassignedAt(assignment.getUnassignedAt())
                .reason(assignment.getReason())
                .build();
    }
}
