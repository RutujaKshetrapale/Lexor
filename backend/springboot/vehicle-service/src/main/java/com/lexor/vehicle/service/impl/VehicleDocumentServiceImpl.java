package com.lexor.vehicle.service.impl;

import com.lexor.vehicle.dto.response.VehicleDocumentResponse;
import com.lexor.vehicle.entity.DocumentVerificationStatus;
import com.lexor.vehicle.entity.DriverProfile;
import com.lexor.vehicle.entity.Vehicle;
import com.lexor.vehicle.entity.VehicleDocument;
import com.lexor.vehicle.entity.VehicleDocumentType;
import com.lexor.vehicle.exception.BadRequestException;
import com.lexor.vehicle.exception.ForbiddenException;
import com.lexor.vehicle.exception.ResourceNotFoundException;
import com.lexor.vehicle.repository.DriverProfileRepository;
import com.lexor.vehicle.repository.VehicleDocumentRepository;
import com.lexor.vehicle.repository.VehicleRepository;
import com.lexor.vehicle.security.UserPrincipal;
import com.lexor.vehicle.service.VehicleDocumentService;
import com.lexor.vehicle.storage.DocumentStorageService;
import com.lexor.vehicle.storage.StoredDocument;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VehicleDocumentServiceImpl implements VehicleDocumentService {

    private static final Logger logger = LoggerFactory.getLogger(VehicleDocumentServiceImpl.class);

    private final VehicleRepository vehicleRepository;
    private final VehicleDocumentRepository documentRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final DocumentStorageService storageService;

    @Override
    @Transactional
    public VehicleDocumentResponse uploadDocument(
            UserPrincipal currentUser,
            String vehicleUuid,
            VehicleDocumentType documentType,
            String documentNumber,
            LocalDate issueDate,
            LocalDate expiryDate,
            String issuingAuthority,
            MultipartFile file
    ) {
        Vehicle vehicle = getVehicleAndVerifyOwnership(currentUser, vehicleUuid);

        // Store file securely (validates magic bytes, extension, path traversal)
        StoredDocument stored = storageService.store(file);

        // Archive previous current version of this document type if one exists
        Optional<VehicleDocument> existingOpt = documentRepository.findByVehicleIdAndDocumentTypeAndIsCurrentTrue(
                vehicle.getId(), documentType
        );

        int version = 1;
        if (existingOpt.isPresent()) {
            VehicleDocument existing = existingOpt.get();
            existing.setCurrent(false);
            documentRepository.save(existing);
            version = existing.getVersion() + 1;
            logger.info("Archived previous version {} of document {} for vehicle {}", existing.getVersion(), documentType, vehicle.getUuid());
        }

        VehicleDocument doc = VehicleDocument.builder()
                .vehicle(vehicle)
                .documentType(documentType)
                .documentNumber(documentNumber)
                .fileName(stored.getOriginalFileName())
                .filePath(stored.getStoragePath())
                .contentType(stored.getContentType())
                .fileSize(stored.getFileSize())
                .issueDate(issueDate)
                .expiryDate(expiryDate)
                .issuingAuthority(issuingAuthority)
                .verificationStatus(DocumentVerificationStatus.PENDING_REVIEW)
                .version(version)
                .isCurrent(true)
                .build();

        doc = documentRepository.save(doc);
        logger.info("Uploaded vehicle document ID {} ({}, version {}) for vehicle {}", doc.getId(), documentType, version, vehicle.getUuid());

        return mapToResponse(doc);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleDocumentResponse> getVehicleDocuments(UserPrincipal currentUser, String vehicleUuid) {
        Vehicle vehicle = getVehicleAndVerifyOwnership(currentUser, vehicleUuid);
        List<VehicleDocument> documents = documentRepository.findByVehicleIdAndIsCurrentTrue(vehicle.getId());
        return documents.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleDocumentResponse getDocumentById(UserPrincipal currentUser, String vehicleUuid, Long documentId) {
        Vehicle vehicle = getVehicleAndVerifyOwnership(currentUser, vehicleUuid);
        VehicleDocument document = documentRepository.findByIdAndVehicleId(documentId, vehicle.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));
        return mapToResponse(document);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadDocument(UserPrincipal currentUser, String vehicleUuid, Long documentId) {
        Vehicle vehicle = getVehicleAndVerifyOwnership(currentUser, vehicleUuid);
        VehicleDocument document = documentRepository.findByIdAndVehicleId(documentId, vehicle.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        return storageService.loadAsResource(document.getFilePath());
    }

    @Override
    @Transactional
    public VehicleDocumentResponse resubmitDocument(
            UserPrincipal currentUser,
            String vehicleUuid,
            Long documentId,
            String documentNumber,
            LocalDate issueDate,
            LocalDate expiryDate,
            String issuingAuthority,
            MultipartFile file
    ) {
        Vehicle vehicle = getVehicleAndVerifyOwnership(currentUser, vehicleUuid);
        VehicleDocument oldDoc = documentRepository.findByIdAndVehicleId(documentId, vehicle.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        if (oldDoc.getVerificationStatus() == DocumentVerificationStatus.VERIFIED) {
            throw new BadRequestException("Cannot resubmit an already verified document");
        }

        StoredDocument stored = storageService.store(file);

        // Mark old document as non-current
        oldDoc.setCurrent(false);
        documentRepository.save(oldDoc);

        VehicleDocument newDoc = VehicleDocument.builder()
                .vehicle(vehicle)
                .documentType(oldDoc.getDocumentType())
                .documentNumber(documentNumber != null ? documentNumber : oldDoc.getDocumentNumber())
                .fileName(stored.getOriginalFileName())
                .filePath(stored.getStoragePath())
                .contentType(stored.getContentType())
                .fileSize(stored.getFileSize())
                .issueDate(issueDate != null ? issueDate : oldDoc.getIssueDate())
                .expiryDate(expiryDate != null ? expiryDate : oldDoc.getExpiryDate())
                .issuingAuthority(issuingAuthority != null ? issuingAuthority : oldDoc.getIssuingAuthority())
                .verificationStatus(DocumentVerificationStatus.PENDING_REVIEW)
                .version(oldDoc.getVersion() + 1)
                .isCurrent(true)
                .build();

        newDoc = documentRepository.save(newDoc);
        logger.info("Resubmitted document ID {} as new version {} for vehicle {}", oldDoc.getId(), newDoc.getVersion(), vehicle.getUuid());

        return mapToResponse(newDoc);
    }

    @Override
    @Transactional
    public void deleteDocument(UserPrincipal currentUser, String vehicleUuid, Long documentId) {
        Vehicle vehicle = getVehicleAndVerifyOwnership(currentUser, vehicleUuid);
        VehicleDocument document = documentRepository.findByIdAndVehicleId(documentId, vehicle.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        if (document.getVerificationStatus() == DocumentVerificationStatus.VERIFIED) {
            throw new BadRequestException("Cannot delete an already verified document required for vehicle compliance");
        }

        // Soft delete: retain record for auditability
        document.setCurrent(false);
        documentRepository.save(document);
        logger.info("Soft-deleted document ID {} for vehicle {}", documentId, vehicle.getUuid());
    }

    private Vehicle getVehicleAndVerifyOwnership(UserPrincipal currentUser, String vehicleUuid) {
        Vehicle vehicle = vehicleRepository.findByUuid(vehicleUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with UUID: " + vehicleUuid));

        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            DriverProfile driver = driverProfileRepository.findByUserId(currentUser.getId())
                    .orElseThrow(() -> new ForbiddenException("No driver profile found for authenticated user"));
            if (!vehicle.getDriver().getId().equals(driver.getId())) {
                throw new ForbiddenException("You do not have permission to access or manage this vehicle's documents");
            }
        }

        return vehicle;
    }

    private VehicleDocumentResponse mapToResponse(VehicleDocument doc) {
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
}
