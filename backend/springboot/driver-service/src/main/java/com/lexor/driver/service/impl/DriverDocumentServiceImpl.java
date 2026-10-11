package com.lexor.driver.service.impl;

import com.lexor.driver.dto.response.DocumentResponse;
import com.lexor.driver.entity.DocumentType;
import com.lexor.driver.entity.DocumentVerificationStatus;
import com.lexor.driver.entity.DriverDocument;
import com.lexor.driver.entity.DriverProfile;
import com.lexor.driver.entity.DriverVerificationHistory;
import com.lexor.driver.entity.User;
import com.lexor.driver.entity.UserStatus;
import com.lexor.driver.exception.BadRequestException;
import com.lexor.driver.exception.ForbiddenException;
import com.lexor.driver.exception.ResourceNotFoundException;
import com.lexor.driver.repository.DriverDocumentRepository;
import com.lexor.driver.repository.DriverProfileRepository;
import com.lexor.driver.repository.DriverVerificationHistoryRepository;
import com.lexor.driver.repository.UserRepository;
import com.lexor.driver.security.UserPrincipal;
import com.lexor.driver.service.DriverDocumentService;
import com.lexor.driver.service.DriverProfileService;
import com.lexor.driver.service.OnboardingPolicyService;
import com.lexor.driver.storage.DocumentStorageService;
import com.lexor.driver.storage.StoredDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DriverDocumentServiceImpl implements DriverDocumentService {

    private final DriverDocumentRepository documentRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final UserRepository userRepository;
    private final DocumentStorageService storageService;
    private final OnboardingPolicyService onboardingPolicyService;
    private final DriverProfileService driverProfileService;
    private final DriverVerificationHistoryRepository verificationHistoryRepository;

    @Override
    @Transactional
    public DocumentResponse uploadDocument(
            UserPrincipal currentUser,
            DocumentType documentType,
            String documentNumber,
            LocalDate issueDate,
            LocalDate expiryDate,
            String issuingAuthority,
            MultipartFile file
    ) {
        User user = getUserOrThrow(currentUser.getUuid());
        DriverProfile driver = driverProfileService.getOrCreateDriverProfile(user);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("Account is not active (" + user.getStatus() + "). Document upload denied.");
        }

        if (issueDate != null && expiryDate != null && !expiryDate.isAfter(issueDate)) {
            throw new BadRequestException("Document expiry date must be later than the issue date");
        }

        StoredDocument stored = storageService.store(file);

        // Check if an active document of this type already exists
        Optional<DriverDocument> existingOpt = documentRepository.findByDriverIdAndDocumentTypeAndIsCurrentTrue(driver.getId(), documentType);
        int nextVersion = 1;
        if (existingOpt.isPresent()) {
            DriverDocument existing = existingOpt.get();
            existing.setCurrent(false);
            documentRepository.save(existing);
            nextVersion = existing.getVersion() + 1;
        }

        DriverDocument document = DriverDocument.builder()
                .driver(driver)
                .documentType(documentType)
                .documentNumber(StringUtils.hasText(documentNumber) ? documentNumber.trim().toUpperCase() : null)
                .fileName(stored.getOriginalFileName())
                .filePath(stored.getStoragePath())
                .contentType(stored.getContentType())
                .fileSize(stored.getFileSize())
                .issueDate(issueDate)
                .expiryDate(expiryDate)
                .issuingAuthority(StringUtils.hasText(issuingAuthority) ? issuingAuthority.trim() : null)
                .verificationStatus(DocumentVerificationStatus.PENDING_REVIEW)
                .version(nextVersion)
                .isCurrent(true)
                .build();

        DriverDocument savedDoc = documentRepository.save(document);

        // Re-evaluate onboarding readiness
        onboardingPolicyService.evaluateAndSyncDriverOnboardingStatus(driver);
        driverProfileRepository.save(driver);

        return mapToResponse(savedDoc);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDriverDocuments(UserPrincipal currentUser) {
        User user = getUserOrThrow(currentUser.getUuid());
        DriverProfile driver = driverProfileService.getOrCreateDriverProfile(user);
        return documentRepository.findByDriverIdAndIsCurrentTrue(driver.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getDriverDocumentById(UserPrincipal currentUser, Long documentId) {
        User user = getUserOrThrow(currentUser.getUuid());
        DriverProfile driver = driverProfileService.getOrCreateDriverProfile(user);

        DriverDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        validateOwnership(driver, document);
        return mapToResponse(document);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadDocument(UserPrincipal currentUser, Long documentId) {
        User user = getUserOrThrow(currentUser.getUuid());
        DriverProfile driver = driverProfileService.getOrCreateDriverProfile(user);

        DriverDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        validateOwnership(driver, document);
        return storageService.loadAsResource(document.getFilePath());
    }

    @Override
    @Transactional
    public DocumentResponse resubmitDocument(
            UserPrincipal currentUser,
            Long documentId,
            MultipartFile file,
            String documentNumber,
            LocalDate issueDate,
            LocalDate expiryDate,
            String issuingAuthority
    ) {
        User user = getUserOrThrow(currentUser.getUuid());
        DriverProfile driver = driverProfileService.getOrCreateDriverProfile(user);

        DriverDocument targetDoc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        validateOwnership(driver, targetDoc);

        if (targetDoc.getVerificationStatus() == DocumentVerificationStatus.VERIFIED) {
            throw new BadRequestException("Document is already VERIFIED. Standard re-upload is required to supersede verified documents.");
        }

        StoredDocument stored = storageService.store(file);

        // Mark target document as no longer current
        targetDoc.setCurrent(false);
        documentRepository.save(targetDoc);

        int nextVersion = targetDoc.getVersion() + 1;
        DriverDocument newVersion = DriverDocument.builder()
                .driver(driver)
                .documentType(targetDoc.getDocumentType())
                .documentNumber(StringUtils.hasText(documentNumber) ? documentNumber.trim().toUpperCase() : targetDoc.getDocumentNumber())
                .fileName(stored.getOriginalFileName())
                .filePath(stored.getStoragePath())
                .contentType(stored.getContentType())
                .fileSize(stored.getFileSize())
                .issueDate(issueDate != null ? issueDate : targetDoc.getIssueDate())
                .expiryDate(expiryDate != null ? expiryDate : targetDoc.getExpiryDate())
                .issuingAuthority(StringUtils.hasText(issuingAuthority) ? issuingAuthority.trim() : targetDoc.getIssuingAuthority())
                .verificationStatus(DocumentVerificationStatus.PENDING_REVIEW)
                .version(nextVersion)
                .isCurrent(true)
                .build();

        DriverDocument saved = documentRepository.save(newVersion);

        // Record history
        verificationHistoryRepository.save(DriverVerificationHistory.builder()
                .driver(driver)
                .document(saved)
                .actionType("DOCUMENT_RESUBMISSION")
                .previousStatus(targetDoc.getVerificationStatus().name())
                .newStatus(DocumentVerificationStatus.PENDING_REVIEW.name())
                .reviewedByAdmin(user)
                .reason("Driver resubmitted document following " + targetDoc.getVerificationStatus())
                .build());

        onboardingPolicyService.evaluateAndSyncDriverOnboardingStatus(driver);
        driverProfileRepository.save(driver);

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public void deleteDocument(UserPrincipal currentUser, Long documentId) {
        User user = getUserOrThrow(currentUser.getUuid());
        DriverProfile driver = driverProfileService.getOrCreateDriverProfile(user);

        DriverDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        validateOwnership(driver, document);

        if (document.getVerificationStatus() == DocumentVerificationStatus.VERIFIED) {
            throw new BadRequestException("Verified compliance documents cannot be deleted by drivers.");
        }

        // Logical removal to preserve audit integrity
        document.setCurrent(false);
        documentRepository.save(document);

        onboardingPolicyService.evaluateAndSyncDriverOnboardingStatus(driver);
        driverProfileRepository.save(driver);
    }

    private User getUserOrThrow(String uuid) {
        return userRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with UUID: " + uuid));
    }

    private void validateOwnership(DriverProfile driver, DriverDocument document) {
        if (!document.getDriver().getId().equals(driver.getId())) {
            throw new ForbiddenException("Access denied: You do not own this document");
        }
    }

    private DocumentResponse mapToResponse(DriverDocument doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .driverId(doc.getDriver().getId())
                .documentType(doc.getDocumentType())
                .documentNumber(maskSensitiveIdentifier(doc.getDocumentNumber()))
                .fileName(doc.getFileName())
                .contentType(doc.getContentType())
                .fileSize(doc.getFileSize())
                .issueDate(doc.getIssueDate())
                .expiryDate(doc.getExpiryDate())
                .issuingAuthority(doc.getIssuingAuthority())
                .verificationStatus(doc.getVerificationStatus())
                .rejectionReason(doc.getRejectionReason())
                .reviewNotes(doc.getReviewNotes())
                .reviewedByAdminId(doc.getReviewedByAdmin() != null ? doc.getReviewedByAdmin().getId() : null)
                .reviewedAt(doc.getReviewedAt())
                .version(doc.getVersion())
                .isCurrent(doc.isCurrent())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    private String maskSensitiveIdentifier(String number) {
        if (!StringUtils.hasText(number)) return null;
        if (number.length() <= 4) return "****";
        return "****" + number.substring(number.length() - 4);
    }
}
