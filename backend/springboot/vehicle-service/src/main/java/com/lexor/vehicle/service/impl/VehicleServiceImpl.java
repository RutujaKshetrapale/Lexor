package com.lexor.vehicle.service.impl;

import com.lexor.vehicle.dto.request.RegisterVehicleRequest;
import com.lexor.vehicle.dto.request.UpdateVehicleRequest;
import com.lexor.vehicle.dto.response.DriverVehicleAssignmentResponse;
import com.lexor.vehicle.dto.response.VehicleResponse;
import com.lexor.vehicle.dto.response.VehicleVerificationHistoryResponse;
import com.lexor.vehicle.dto.response.VehicleVerificationStatusResponse;
import com.lexor.vehicle.entity.AssignmentStatus;
import com.lexor.vehicle.entity.DocumentVerificationStatus;
import com.lexor.vehicle.entity.DriverProfile;
import com.lexor.vehicle.entity.DriverVehicleAssignment;
import com.lexor.vehicle.entity.Role;
import com.lexor.vehicle.entity.Vehicle;
import com.lexor.vehicle.entity.VehicleDocument;
import com.lexor.vehicle.entity.VehicleDocumentType;
import com.lexor.vehicle.entity.VehicleVerificationHistory;
import com.lexor.vehicle.entity.VehicleVerificationStatus;
import com.lexor.vehicle.exception.BadRequestException;
import com.lexor.vehicle.exception.DuplicateResourceException;
import com.lexor.vehicle.exception.ForbiddenException;
import com.lexor.vehicle.exception.InvalidStatusTransitionException;
import com.lexor.vehicle.exception.ResourceNotFoundException;
import com.lexor.vehicle.repository.DriverProfileRepository;
import com.lexor.vehicle.repository.DriverVehicleAssignmentRepository;
import com.lexor.vehicle.repository.VehicleDocumentRepository;
import com.lexor.vehicle.repository.VehicleRepository;
import com.lexor.vehicle.repository.VehicleVerificationHistoryRepository;
import com.lexor.vehicle.security.UserPrincipal;
import com.lexor.vehicle.service.VehicleService;
import com.lexor.vehicle.service.VehicleVerificationPolicyService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private static final Logger logger = LoggerFactory.getLogger(VehicleServiceImpl.class);

    private final VehicleRepository vehicleRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final VehicleDocumentRepository documentRepository;
    private final VehicleVerificationHistoryRepository historyRepository;
    private final DriverVehicleAssignmentRepository assignmentRepository;
    private final VehicleVerificationPolicyService policyService;

    @Override
    @Transactional
    public VehicleResponse registerVehicle(UserPrincipal currentUser, RegisterVehicleRequest request) {
        DriverProfile driver = getAuthenticatedDriver(currentUser);

        String normalizedPlate = normalizeLicensePlate(request.getLicensePlate());
        if (vehicleRepository.existsByLicensePlate(normalizedPlate)) {
            throw new DuplicateResourceException("A vehicle with license plate " + normalizedPlate + " is already registered");
        }

        if (StringUtils.hasText(request.getVin())) {
            String trimmedVin = request.getVin().trim().toUpperCase();
            if (vehicleRepository.existsByVin(trimmedVin)) {
                throw new DuplicateResourceException("A vehicle with VIN " + trimmedVin + " is already registered");
            }
        }

        Vehicle vehicle = Vehicle.builder()
                .uuid(UUID.randomUUID().toString())
                .driver(driver)
                .make(request.getMake().trim())
                .model(request.getModel().trim())
                .year(request.getYear())
                .color(request.getColor().trim())
                .licensePlate(normalizedPlate)
                .vin(StringUtils.hasText(request.getVin()) ? request.getVin().trim().toUpperCase() : null)
                .rideTypeCategory(request.getRideTypeCategory().trim().toUpperCase())
                .seatCapacity(request.getSeatCapacity())
                .fuelType(request.getFuelType())
                .isActive(true)
                .isInspected(false)
                .verificationStatus(VehicleVerificationStatus.PENDING_VERIFICATION)
                .build();

        vehicle = vehicleRepository.save(vehicle);
        logger.info("Driver {} registered new vehicle {} ({})", driver.getId(), vehicle.getUuid(), vehicle.getLicensePlate());

        // Record initial assignment
        DriverVehicleAssignment assignment = DriverVehicleAssignment.builder()
                .vehicle(vehicle)
                .driver(driver)
                .status(AssignmentStatus.ACTIVE)
                .assignedAt(LocalDateTime.now())
                .reason("Initial vehicle registration by owning driver")
                .build();
        assignmentRepository.save(assignment);

        return mapToResponse(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleResponse> getMyVehicles(UserPrincipal currentUser) {
        DriverProfile driver = getAuthenticatedDriver(currentUser);
        List<Vehicle> vehicles = vehicleRepository.findByDriverId(driver.getId());
        return vehicles.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleResponse getVehicleByUuid(UserPrincipal currentUser, String vehicleUuid) {
        Vehicle vehicle = getVehicleAndVerifyOwnership(currentUser, vehicleUuid);
        return mapToResponse(vehicle);
    }

    @Override
    @Transactional
    public VehicleResponse updateVehicle(UserPrincipal currentUser, String vehicleUuid, UpdateVehicleRequest request) {
        Vehicle vehicle = getVehicleAndVerifyOwnership(currentUser, vehicleUuid);

        if (vehicle.getVerificationStatus() == VehicleVerificationStatus.SUSPENDED) {
            throw new InvalidStatusTransitionException("Cannot update details of a suspended vehicle. Please contact administration.");
        }

        if (StringUtils.hasText(request.getMake())) {
            vehicle.setMake(request.getMake().trim());
        }
        if (StringUtils.hasText(request.getModel())) {
            vehicle.setModel(request.getModel().trim());
        }
        if (request.getYear() != null) {
            vehicle.setYear(request.getYear());
        }
        if (StringUtils.hasText(request.getColor())) {
            vehicle.setColor(request.getColor().trim());
        }
        if (StringUtils.hasText(request.getVin())) {
            String trimmedVin = request.getVin().trim().toUpperCase();
            if (vehicleRepository.existsByVinAndIdNot(trimmedVin, vehicle.getId())) {
                throw new DuplicateResourceException("VIN " + trimmedVin + " is already in use by another vehicle");
            }
            vehicle.setVin(trimmedVin);
        }
        if (StringUtils.hasText(request.getRideTypeCategory())) {
            vehicle.setRideTypeCategory(request.getRideTypeCategory().trim().toUpperCase());
        }
        if (request.getSeatCapacity() != null) {
            vehicle.setSeatCapacity(request.getSeatCapacity());
        }
        if (request.getFuelType() != null) {
            vehicle.setFuelType(request.getFuelType());
        }

        vehicle = vehicleRepository.save(vehicle);
        logger.info("Updated vehicle details for {}", vehicle.getUuid());
        return mapToResponse(vehicle);
    }

    @Override
    @Transactional
    public VehicleResponse updateVehicleStatus(UserPrincipal currentUser, String vehicleUuid, boolean isActive) {
        Vehicle vehicle = getVehicleAndVerifyOwnership(currentUser, vehicleUuid);

        if (isActive && vehicle.getVerificationStatus() != VehicleVerificationStatus.VERIFIED) {
            throw new InvalidStatusTransitionException("Cannot activate an unverified or suspended vehicle. Status is: " + vehicle.getVerificationStatus());
        }

        vehicle.setActive(isActive);
        vehicle = vehicleRepository.save(vehicle);
        logger.info("Toggled active status for vehicle {} to {}", vehicle.getUuid(), isActive);
        return mapToResponse(vehicle);
    }

    @Override
    @Transactional
    public VehicleResponse submitForVerification(UserPrincipal currentUser, String vehicleUuid) {
        Vehicle vehicle = getVehicleAndVerifyOwnership(currentUser, vehicleUuid);

        if (vehicle.getVerificationStatus() == VehicleVerificationStatus.SUSPENDED) {
            throw new InvalidStatusTransitionException("Cannot submit a suspended vehicle for verification. Contact support for reinstatement.");
        }
        if (vehicle.getVerificationStatus() == VehicleVerificationStatus.VERIFIED) {
            throw new InvalidStatusTransitionException("Vehicle is already verified.");
        }

        List<VehicleDocument> documents = documentRepository.findByVehicleIdAndIsCurrentTrue(vehicle.getId());
        List<VehicleDocumentType> missing = policyService.getMissingDocuments(documents);
        if (!missing.isEmpty()) {
            throw new BadRequestException("Cannot submit for verification. The following mandatory compliance documents are missing: " + missing);
        }

        VehicleVerificationStatus oldStatus = vehicle.getVerificationStatus();
        vehicle.setVerificationStatus(VehicleVerificationStatus.PENDING_VERIFICATION);
        vehicle.setRejectionReason(null);
        vehicle = vehicleRepository.save(vehicle);

        logger.info("Vehicle {} submitted for administrative verification (previous status: {})", vehicle.getUuid(), oldStatus);
        return mapToResponse(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleVerificationStatusResponse getVerificationStatus(UserPrincipal currentUser, String vehicleUuid) {
        Vehicle vehicle = getVehicleAndVerifyOwnership(currentUser, vehicleUuid);
        List<VehicleDocument> documents = documentRepository.findByVehicleIdAndIsCurrentTrue(vehicle.getId());

        List<VehicleDocumentType> required = policyService.getRequiredDocumentTypes();
        List<VehicleDocumentType> uploaded = documents.stream()
                .map(VehicleDocument::getDocumentType)
                .collect(Collectors.toList());
        List<VehicleDocumentType> missing = policyService.getMissingDocuments(documents);
        List<VehicleDocumentType> pendingReview = documents.stream()
                .filter(d -> d.getVerificationStatus() == DocumentVerificationStatus.PENDING_REVIEW || d.getVerificationStatus() == DocumentVerificationStatus.UNDER_REVIEW)
                .map(VehicleDocument::getDocumentType)
                .collect(Collectors.toList());
        List<VehicleDocumentType> rejected = documents.stream()
                .filter(d -> d.getVerificationStatus() == DocumentVerificationStatus.REJECTED || d.getVerificationStatus() == DocumentVerificationStatus.RESUBMISSION_REQUIRED)
                .map(VehicleDocument::getDocumentType)
                .collect(Collectors.toList());

        boolean isEligible = vehicle.getVerificationStatus() == VehicleVerificationStatus.VERIFIED && vehicle.isActive();

        return VehicleVerificationStatusResponse.builder()
                .vehicleUuid(vehicle.getUuid())
                .status(vehicle.getVerificationStatus())
                .rejectionReason(vehicle.getRejectionReason())
                .isEligibleForAssignment(isEligible)
                .requiredDocuments(required)
                .uploadedDocuments(uploaded)
                .missingDocuments(missing)
                .pendingReviewDocuments(pendingReview)
                .rejectedDocuments(rejected)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleVerificationHistoryResponse> getVehicleVerificationHistory(UserPrincipal currentUser, String vehicleUuid) {
        Vehicle vehicle = getVehicleAndVerifyOwnership(currentUser, vehicleUuid);
        List<VehicleVerificationHistory> history = historyRepository.findByVehicleIdOrderByCreatedAtDesc(vehicle.getId());
        return history.stream().map(this::mapToHistoryResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DriverVehicleAssignmentResponse> getVehicleAssignments(UserPrincipal currentUser, String vehicleUuid) {
        Vehicle vehicle = getVehicleAndVerifyOwnership(currentUser, vehicleUuid);
        List<DriverVehicleAssignment> assignments = assignmentRepository.findByVehicleIdOrderByAssignedAtDesc(vehicle.getId());
        return assignments.stream().map(this::mapToAssignmentResponse).collect(Collectors.toList());
    }

    private DriverProfile getAuthenticatedDriver(UserPrincipal currentUser) {
        return driverProfileRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ForbiddenException("No driver profile found for authenticated user"));
    }

    private Vehicle getVehicleAndVerifyOwnership(UserPrincipal currentUser, String vehicleUuid) {
        Vehicle vehicle = vehicleRepository.findByUuid(vehicleUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with UUID: " + vehicleUuid));

        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            DriverProfile driver = getAuthenticatedDriver(currentUser);
            if (!vehicle.getDriver().getId().equals(driver.getId())) {
                throw new ForbiddenException("You do not have permission to access or manage this vehicle");
            }
        }

        return vehicle;
    }

    private String normalizeLicensePlate(String plate) {
        if (!StringUtils.hasText(plate)) {
            throw new BadRequestException("License plate cannot be blank");
        }
        // Normalize: uppercase, replace whitespace/consecutive dashes with single dash
        return plate.trim().toUpperCase().replaceAll("\\s+", "-").replaceAll("-+", "-");
    }

    private VehicleResponse mapToResponse(Vehicle vehicle) {
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
