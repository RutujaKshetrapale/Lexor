package com.lexor.driver.service.impl;

import com.lexor.driver.dto.request.DriverSuspensionRequest;
import com.lexor.driver.dto.request.ReviewDocumentRequest;
import com.lexor.driver.dto.request.VerifyDriverRequest;
import com.lexor.driver.dto.response.DocumentResponse;
import com.lexor.driver.dto.response.DriverProfileResponse;
import com.lexor.driver.dto.response.PagedResponse;
import com.lexor.driver.dto.response.VerificationHistoryResponse;
import com.lexor.driver.entity.DocumentType;
import com.lexor.driver.entity.DocumentVerificationStatus;
import com.lexor.driver.entity.DriverDocument;
import com.lexor.driver.entity.DriverProfile;
import com.lexor.driver.entity.DriverStatus;
import com.lexor.driver.entity.DriverVerificationHistory;
import com.lexor.driver.entity.OnboardingStatus;
import com.lexor.driver.entity.User;
import com.lexor.driver.entity.UserStatus;
import com.lexor.driver.exception.BadRequestException;
import com.lexor.driver.exception.ResourceNotFoundException;
import com.lexor.driver.repository.DriverDocumentRepository;
import com.lexor.driver.repository.DriverProfileRepository;
import com.lexor.driver.repository.DriverVerificationHistoryRepository;
import com.lexor.driver.repository.UserRepository;
import com.lexor.driver.security.UserPrincipal;
import com.lexor.driver.service.DriverProfileService;
import com.lexor.driver.service.DriverVerificationService;
import com.lexor.driver.service.OnboardingPolicyService;
import com.lexor.driver.storage.DocumentStorageService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DriverVerificationServiceImpl implements DriverVerificationService {

    private final DriverProfileRepository driverProfileRepository;
    private final DriverDocumentRepository documentRepository;
    private final DriverVerificationHistoryRepository verificationHistoryRepository;
    private final UserRepository userRepository;
    private final DocumentStorageService storageService;
    private final DriverProfileService driverProfileService;
    private final OnboardingPolicyService onboardingPolicyService;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<DriverProfileResponse> listDrivers(
            OnboardingStatus verificationStatus,
            UserStatus accountStatus,
            DriverStatus availabilityStatus,
            String search,
            int page,
            int size,
            String sortBy,
            String sortDir
    ) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Specification<DriverProfile> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (verificationStatus != null) {
                predicates.add(cb.equal(root.get("onboardingStatus"), verificationStatus));
            }
            if (availabilityStatus != null) {
                predicates.add(cb.equal(root.get("status"), availabilityStatus));
            }
            if (accountStatus != null) {
                predicates.add(cb.equal(root.join("user").get("status"), accountStatus));
            }
            if (StringUtils.hasText(search)) {
                String term = "%" + search.trim().toLowerCase() + "%";
                Predicate namePred = cb.like(cb.lower(root.join("user").get("firstName")), term);
                Predicate lastNamePred = cb.like(cb.lower(root.join("user").get("lastName")), term);
                Predicate emailPred = cb.like(cb.lower(root.join("user").get("email")), term);
                Predicate phonePred = cb.like(cb.lower(root.join("user").get("phoneNumber")), term);
                Predicate licensePred = cb.like(cb.lower(root.get("licenseNumber")), term);
                predicates.add(cb.or(namePred, lastNamePred, emailPred, phonePred, licensePred));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<DriverProfile> pageResult = driverProfileRepository.findAll(spec, pageable);
        List<DriverProfileResponse> content = pageResult.getContent().stream()
                .map(driverProfileService::mapToResponse)
                .collect(Collectors.toList());

        return PagedResponse.<DriverProfileResponse>builder()
                .content(content)
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .last(pageResult.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DriverProfileResponse getDriverByUuid(String driverUuid) {
        DriverProfile driver = driverProfileRepository.findByUserUuid(driverUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Driver profile not found with user UUID: " + driverUuid));
        return driverProfileService.mapToResponse(driver);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDriverDocumentsByUuid(String driverUuid) {
        DriverProfile driver = driverProfileRepository.findByUserUuid(driverUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Driver profile not found with user UUID: " + driverUuid));
        return documentRepository.findByDriverId(driver.getId())
                .stream()
                .map(this::mapDocToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<DocumentResponse> listAwaitingDocuments(
            DocumentVerificationStatus status,
            DocumentType documentType,
            String driverUuid,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Specification<DriverDocument> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.isTrue(root.get("isCurrent")));

            if (status != null) {
                predicates.add(cb.equal(root.get("verificationStatus"), status));
            } else {
                predicates.add(cb.equal(root.get("verificationStatus"), DocumentVerificationStatus.PENDING_REVIEW));
            }

            if (documentType != null) {
                predicates.add(cb.equal(root.get("documentType"), documentType));
            }

            if (StringUtils.hasText(driverUuid)) {
                predicates.add(cb.equal(root.join("driver").join("user").get("uuid"), driverUuid.trim()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<DriverDocument> pageResult = documentRepository.findAll(spec, pageable);
        List<DocumentResponse> content = pageResult.getContent().stream()
                .map(this::mapDocToResponse)
                .collect(Collectors.toList());

        return PagedResponse.<DocumentResponse>builder()
                .content(content)
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .last(pageResult.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadDocumentForAdmin(Long documentId) {
        DriverDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));
        return storageService.loadAsResource(document.getFilePath());
    }

    @Override
    @Transactional
    public DocumentResponse reviewDocument(UserPrincipal adminUser, Long documentId, ReviewDocumentRequest request) {
        User admin = userRepository.findByUuid(adminUser.getUuid())
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found with UUID: " + adminUser.getUuid()));

        DriverDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        if (request.getStatus() == DocumentVerificationStatus.REJECTED || request.getStatus() == DocumentVerificationStatus.RESUBMISSION_REQUIRED) {
            if (!StringUtils.hasText(request.getRejectionReason()) && !StringUtils.hasText(request.getReviewNotes())) {
                throw new BadRequestException("Rejection reason or review notes are mandatory when rejecting a document or requesting resubmission");
            }
        }

        String previousStatus = document.getVerificationStatus().name();
        document.setVerificationStatus(request.getStatus());
        document.setRejectionReason(request.getRejectionReason());
        document.setReviewNotes(request.getReviewNotes());
        document.setReviewedByAdmin(admin);
        document.setReviewedAt(LocalDateTime.now());

        DriverDocument savedDoc = documentRepository.save(document);

        // Record verification audit
        verificationHistoryRepository.save(DriverVerificationHistory.builder()
                .driver(document.getDriver())
                .document(savedDoc)
                .actionType("DOCUMENT_REVIEW")
                .previousStatus(previousStatus)
                .newStatus(request.getStatus().name())
                .reviewedByAdmin(admin)
                .reason(StringUtils.hasText(request.getRejectionReason()) ? request.getRejectionReason() : request.getReviewNotes())
                .build());

        // Recalculate driver's overall onboarding status
        DriverProfile driver = document.getDriver();
        onboardingPolicyService.evaluateAndSyncDriverOnboardingStatus(driver);
        driverProfileRepository.save(driver);

        return mapDocToResponse(savedDoc);
    }

    @Override
    @Transactional
    public DriverProfileResponse reviewDriverVerification(UserPrincipal adminUser, String driverUuid, VerifyDriverRequest request) {
        User admin = userRepository.findByUuid(adminUser.getUuid())
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found with UUID: " + adminUser.getUuid()));

        DriverProfile driver = driverProfileRepository.findByUserUuid(driverUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Driver profile not found with user UUID: " + driverUuid));

        String previousStatus = driver.getOnboardingStatus().name();

        if (request.getStatus() == OnboardingStatus.VERIFIED) {
            if (!onboardingPolicyService.isProfileComplete(driver)) {
                throw new BadRequestException("Cannot verify driver: Personal or licence profile information is incomplete");
            }
            if (!onboardingPolicyService.areAllRequiredDocumentsVerified(driver.getId())) {
                throw new BadRequestException("Cannot verify driver: All required documents must be marked VERIFIED prior to approval");
            }
            driver.setVerified(true);
            driver.setVerifiedAt(LocalDateTime.now());
            driver.setOnboardingStatus(OnboardingStatus.VERIFIED);
            driver.setRejectionReason(null);
        } else if (request.getStatus() == OnboardingStatus.REJECTED) {
            if (!StringUtils.hasText(request.getNotes())) {
                throw new BadRequestException("Rejection explanation notes are required when setting driver status to REJECTED");
            }
            driver.setVerified(false);
            driver.setOnboardingStatus(OnboardingStatus.REJECTED);
            driver.setRejectionReason(request.getNotes().trim());
        } else if (request.getStatus() == OnboardingStatus.UNDER_REVIEW) {
            driver.setOnboardingStatus(OnboardingStatus.UNDER_REVIEW);
        } else {
            throw new BadRequestException("Unsupported administrative verification status: " + request.getStatus());
        }

        DriverProfile saved = driverProfileRepository.save(driver);

        // Record verification audit
        verificationHistoryRepository.save(DriverVerificationHistory.builder()
                .driver(saved)
                .document(null)
                .actionType("DRIVER_VERIFICATION")
                .previousStatus(previousStatus)
                .newStatus(request.getStatus().name())
                .reviewedByAdmin(admin)
                .reason(request.getNotes())
                .build());

        return driverProfileService.mapToResponse(saved);
    }

    @Override
    @Transactional
    public DriverProfileResponse updateDriverSuspension(UserPrincipal adminUser, String driverUuid, DriverSuspensionRequest request) {
        User admin = userRepository.findByUuid(adminUser.getUuid())
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found with UUID: " + adminUser.getUuid()));

        DriverProfile driver = driverProfileRepository.findByUserUuid(driverUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Driver profile not found with user UUID: " + driverUuid));

        String previousStatus = driver.getOnboardingStatus().name();

        if (request.getSuspended()) {
            driver.setStatus(DriverStatus.SUSPENDED);
            driver.setOnboardingStatus(OnboardingStatus.SUSPENDED);
            driver.setRejectionReason(request.getReason());
        } else {
            // Reinstating
            driver.setStatus(DriverStatus.OFFLINE);
            if (onboardingPolicyService.areAllRequiredDocumentsVerified(driver.getId()) && onboardingPolicyService.isProfileComplete(driver)) {
                driver.setOnboardingStatus(OnboardingStatus.VERIFIED);
                driver.setVerified(true);
            } else {
                driver.setOnboardingStatus(OnboardingStatus.PENDING_REVIEW);
                driver.setVerified(false);
            }
            driver.setRejectionReason(null);
        }

        DriverProfile saved = driverProfileRepository.save(driver);

        verificationHistoryRepository.save(DriverVerificationHistory.builder()
                .driver(saved)
                .document(null)
                .actionType("DRIVER_SUSPENSION")
                .previousStatus(previousStatus)
                .newStatus(saved.getOnboardingStatus().name())
                .reviewedByAdmin(admin)
                .reason(request.getReason())
                .build());

        return driverProfileService.mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VerificationHistoryResponse> getDriverVerificationHistory(String driverUuid) {
        return verificationHistoryRepository.findByDriverUserUuidOrderByCreatedAtDesc(driverUuid)
                .stream()
                .map(h -> VerificationHistoryResponse.builder()
                        .id(h.getId())
                        .driverId(h.getDriver().getId())
                        .documentId(h.getDocument() != null ? h.getDocument().getId() : null)
                        .actionType(h.getActionType())
                        .previousStatus(h.getPreviousStatus())
                        .newStatus(h.getNewStatus())
                        .reviewedByAdminId(h.getReviewedByAdmin().getId())
                        .reviewedByAdminName(h.getReviewedByAdmin().getFirstName() + " " + h.getReviewedByAdmin().getLastName())
                        .reason(h.getReason())
                        .createdAt(h.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private DocumentResponse mapDocToResponse(DriverDocument doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .driverId(doc.getDriver().getId())
                .documentType(doc.getDocumentType())
                .documentNumber(doc.getDocumentNumber()) // full identifier accessible to admin
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
}
