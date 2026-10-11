package com.lexor.driver.service.impl;

import com.lexor.driver.config.OnboardingPolicyConfig;
import com.lexor.driver.dto.response.DocumentRequirementStatus;
import com.lexor.driver.dto.response.DriverOnboardingResponse;
import com.lexor.driver.entity.DocumentType;
import com.lexor.driver.entity.DocumentVerificationStatus;
import com.lexor.driver.entity.DriverDocument;
import com.lexor.driver.entity.DriverProfile;
import com.lexor.driver.entity.OnboardingStatus;
import com.lexor.driver.repository.DriverDocumentRepository;
import com.lexor.driver.repository.EmergencyContactRepository;
import com.lexor.driver.service.OnboardingPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OnboardingPolicyServiceImpl implements OnboardingPolicyService {

    private final OnboardingPolicyConfig policyConfig;
    private final DriverDocumentRepository documentRepository;
    private final EmergencyContactRepository emergencyContactRepository;

    @Override
    public DriverOnboardingResponse calculateOnboardingProgress(DriverProfile driver) {
        List<DocumentType> requiredTypes = policyConfig.getRequiredDocumentTypes();
        List<DriverDocument> currentDocs = documentRepository.findByDriverIdAndIsCurrentTrue(driver.getId());

        Map<DocumentType, DriverDocument> docMap = new HashMap<>();
        for (DriverDocument doc : currentDocs) {
            docMap.put(doc.getDocumentType(), doc);
        }

        List<DocumentRequirementStatus> checklist = new ArrayList<>();
        List<String> outstandingTasks = new ArrayList<>();

        boolean profileComplete = isProfileComplete(driver);
        if (!profileComplete) {
            if (!StringUtils.hasText(driver.getLicenseNumber()) || driver.getLicenseExpiryDate() == null) {
                outstandingTasks.add("Provide valid driving license number and future expiry date");
            }
            if (driver.getDateOfBirth() == null) {
                outstandingTasks.add("Provide date of birth (driver must be at least 18 years old)");
            } else if (Period.between(driver.getDateOfBirth(), LocalDate.now()).getYears() < 18) {
                outstandingTasks.add("Driver must be at least 18 years of age");
            }
            if (!StringUtils.hasText(driver.getResidentialAddress()) || !StringUtils.hasText(driver.getCity())) {
                outstandingTasks.add("Provide complete residential address and city");
            }
            if (driver.getUser() == null || emergencyContactRepository.findFirstByUserIdOrderByCreatedAtDesc(driver.getUser().getId()).isEmpty()) {
                outstandingTasks.add("Add emergency contact information");
            }
        }

        int submittedDocsCount = 0;
        int verifiedDocsCount = 0;
        boolean allRequiredSubmitted = true;
        boolean allRequiredVerified = true;
        boolean anyRejected = false;
        String latestRejectionReason = null;

        for (DocumentType type : requiredTypes) {
            DriverDocument doc = docMap.get(type);
            DocumentVerificationStatus status = doc != null ? doc.getVerificationStatus() : DocumentVerificationStatus.NOT_SUBMITTED;

            boolean isSubmitted = (status != DocumentVerificationStatus.NOT_SUBMITTED);
            boolean isVerified = (status == DocumentVerificationStatus.VERIFIED);
            boolean isRejected = (status == DocumentVerificationStatus.REJECTED || status == DocumentVerificationStatus.RESUBMISSION_REQUIRED);

            if (isSubmitted) {
                submittedDocsCount++;
            } else {
                allRequiredSubmitted = false;
                outstandingTasks.add("Upload required document: " + formatDocTypeName(type));
            }

            if (isVerified) {
                verifiedDocsCount++;
            } else {
                allRequiredVerified = false;
            }

            if (isRejected) {
                anyRejected = true;
                latestRejectionReason = doc.getRejectionReason();
                outstandingTasks.add("Resubmit rejected document: " + formatDocTypeName(type) + " (Reason: " + doc.getRejectionReason() + ")");
            }

            checklist.add(DocumentRequirementStatus.builder()
                    .documentType(type)
                    .required(true)
                    .status(status)
                    .documentId(doc != null ? doc.getId() : null)
                    .documentNumber(doc != null ? maskSensitiveNumber(doc.getDocumentNumber()) : null)
                    .rejectionReason(doc != null ? doc.getRejectionReason() : null)
                    .uploadedAt(doc != null ? doc.getCreatedAt() : null)
                    .verifiedAt(doc != null ? doc.getReviewedAt() : null)
                    .build());
        }

        // Add any non-required documents driver uploaded
        for (DriverDocument doc : currentDocs) {
            if (!requiredTypes.contains(doc.getDocumentType())) {
                checklist.add(DocumentRequirementStatus.builder()
                        .documentType(doc.getDocumentType())
                        .required(false)
                        .status(doc.getVerificationStatus())
                        .documentId(doc.getId())
                        .documentNumber(maskSensitiveNumber(doc.getDocumentNumber()))
                        .rejectionReason(doc.getRejectionReason())
                        .uploadedAt(doc.getCreatedAt())
                        .verifiedAt(doc.getReviewedAt())
                        .build());
            }
        }

        int profileScore = profileComplete ? 50 : calculateProfileScore(driver);
        int docScore = requiredTypes.isEmpty() ? 50 : (int) Math.round((submittedDocsCount * 50.0) / requiredTypes.size());
        int totalPercentage = Math.min(100, profileScore + docScore);

        boolean canSubmit = profileComplete && allRequiredSubmitted && !anyRejected &&
                (driver.getOnboardingStatus() == OnboardingStatus.INCOMPLETE || driver.getOnboardingStatus() == OnboardingStatus.REJECTED);

        boolean eligibleForOnline = (driver.getOnboardingStatus() == OnboardingStatus.VERIFIED && driver.isVerified());

        return DriverOnboardingResponse.builder()
                .driverUuid(driver.getUser() != null ? driver.getUser().getUuid() : null)
                .onboardingStatus(driver.getOnboardingStatus())
                .completionPercentage(totalPercentage)
                .profileComplete(profileComplete)
                .documentsComplete(allRequiredVerified)
                .canSubmitForReview(canSubmit)
                .isEligibleForOnline(eligibleForOnline)
                .documentChecklist(checklist)
                .outstandingTasks(outstandingTasks)
                .rejectionReason(anyRejected ? latestRejectionReason : driver.getRejectionReason())
                .build();
    }

    @Override
    public boolean isProfileComplete(DriverProfile driver) {
        if (!StringUtils.hasText(driver.getLicenseNumber())) return false;
        if (driver.getLicenseExpiryDate() == null || driver.getLicenseExpiryDate().isBefore(LocalDate.now())) return false;
        if (driver.getDateOfBirth() == null) return false;
        if (Period.between(driver.getDateOfBirth(), LocalDate.now()).getYears() < 18) return false;
        if (!StringUtils.hasText(driver.getResidentialAddress())) return false;
        if (!StringUtils.hasText(driver.getCity())) return false;
        if (!StringUtils.hasText(driver.getState())) return false;
        if (!StringUtils.hasText(driver.getPostalCode())) return false;
        if (driver.getUser() == null) return false;
        return emergencyContactRepository.findFirstByUserIdOrderByCreatedAtDesc(driver.getUser().getId()).isPresent();
    }

    @Override
    public boolean areAllRequiredDocumentsVerified(Long driverId) {
        List<DocumentType> required = policyConfig.getRequiredDocumentTypes();
        List<DriverDocument> currentDocs = documentRepository.findByDriverIdAndIsCurrentTrue(driverId);
        Map<DocumentType, DriverDocument> docMap = new HashMap<>();
        for (DriverDocument d : currentDocs) {
            docMap.put(d.getDocumentType(), d);
        }

        for (DocumentType req : required) {
            DriverDocument doc = docMap.get(req);
            if (doc == null || doc.getVerificationStatus() != DocumentVerificationStatus.VERIFIED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean areAllRequiredDocumentsSubmitted(Long driverId) {
        List<DocumentType> required = policyConfig.getRequiredDocumentTypes();
        List<DriverDocument> currentDocs = documentRepository.findByDriverIdAndIsCurrentTrue(driverId);
        Map<DocumentType, DriverDocument> docMap = new HashMap<>();
        for (DriverDocument d : currentDocs) {
            docMap.put(d.getDocumentType(), d);
        }

        for (DocumentType req : required) {
            DriverDocument doc = docMap.get(req);
            if (doc == null || doc.getVerificationStatus() == DocumentVerificationStatus.NOT_SUBMITTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void evaluateAndSyncDriverOnboardingStatus(DriverProfile driver) {
        if (driver.getOnboardingStatus() == OnboardingStatus.SUSPENDED) {
            return;
        }

        List<DriverDocument> currentDocs = documentRepository.findByDriverIdAndIsCurrentTrue(driver.getId());
        boolean hasRejected = currentDocs.stream().anyMatch(d ->
                d.getVerificationStatus() == DocumentVerificationStatus.REJECTED ||
                d.getVerificationStatus() == DocumentVerificationStatus.RESUBMISSION_REQUIRED);

        if (hasRejected) {
            driver.setOnboardingStatus(OnboardingStatus.REJECTED);
            driver.setVerified(false);
            return;
        }

        boolean profileComplete = isProfileComplete(driver);
        boolean docsVerified = areAllRequiredDocumentsVerified(driver.getId());

        if (profileComplete && docsVerified) {
            driver.setOnboardingStatus(OnboardingStatus.VERIFIED);
            driver.setVerified(true);
            if (driver.getVerifiedAt() == null) {
                driver.setVerifiedAt(java.time.LocalDateTime.now());
            }
        } else if (driver.getOnboardingStatus() == OnboardingStatus.VERIFIED) {
            // A verified driver had a document superseded or changed
            driver.setOnboardingStatus(OnboardingStatus.PENDING_REVIEW);
            driver.setVerified(false);
        }
    }

    private int calculateProfileScore(DriverProfile driver) {
        int score = 0;
        if (StringUtils.hasText(driver.getLicenseNumber()) && driver.getLicenseExpiryDate() != null) score += 15;
        if (driver.getDateOfBirth() != null && Period.between(driver.getDateOfBirth(), LocalDate.now()).getYears() >= 18) score += 10;
        if (StringUtils.hasText(driver.getResidentialAddress()) && StringUtils.hasText(driver.getCity())) score += 15;
        if (driver.getUser() != null && emergencyContactRepository.findFirstByUserIdOrderByCreatedAtDesc(driver.getUser().getId()).isPresent()) score += 10;
        return score;
    }

    private String formatDocTypeName(DocumentType type) {
        return type.name().replace('_', ' ');
    }

    private String maskSensitiveNumber(String number) {
        if (!StringUtils.hasText(number)) return null;
        if (number.length() <= 4) return "****";
        return "****" + number.substring(number.length() - 4);
    }
}
