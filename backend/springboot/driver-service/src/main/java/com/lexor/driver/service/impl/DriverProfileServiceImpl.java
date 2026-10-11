package com.lexor.driver.service.impl;

import com.lexor.driver.dto.EmergencyContactDto;
import com.lexor.driver.dto.request.DriverAvailabilityRequest;
import com.lexor.driver.dto.request.PatchDriverProfileRequest;
import com.lexor.driver.dto.request.UpdateDriverProfileRequest;
import com.lexor.driver.dto.response.DriverAvailabilityResponse;
import com.lexor.driver.dto.response.DriverOnboardingResponse;
import com.lexor.driver.dto.response.DriverProfileResponse;
import com.lexor.driver.entity.DriverProfile;
import com.lexor.driver.entity.DriverStatus;
import com.lexor.driver.entity.DriverVerificationHistory;
import com.lexor.driver.entity.EmergencyContact;
import com.lexor.driver.entity.OnboardingStatus;
import com.lexor.driver.entity.User;
import com.lexor.driver.entity.UserStatus;
import com.lexor.driver.exception.BadRequestException;
import com.lexor.driver.exception.DuplicateResourceException;
import com.lexor.driver.exception.ForbiddenException;
import com.lexor.driver.exception.ResourceNotFoundException;
import com.lexor.driver.repository.DriverProfileRepository;
import com.lexor.driver.repository.DriverVerificationHistoryRepository;
import com.lexor.driver.repository.EmergencyContactRepository;
import com.lexor.driver.repository.UserRepository;
import com.lexor.driver.security.UserPrincipal;
import com.lexor.driver.service.DriverProfileService;
import com.lexor.driver.service.OnboardingPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DriverProfileServiceImpl implements DriverProfileService {

    private final DriverProfileRepository driverProfileRepository;
    private final UserRepository userRepository;
    private final EmergencyContactRepository emergencyContactRepository;
    private final OnboardingPolicyService onboardingPolicyService;
    private final DriverVerificationHistoryRepository verificationHistoryRepository;

    @Override
    @Transactional
    public DriverProfileResponse getDriverProfile(UserPrincipal currentUser) {
        User user = getUserOrThrow(currentUser.getUuid());
        DriverProfile driver = getOrCreateDriverProfile(user);
        return mapToResponse(driver);
    }

    @Override
    @Transactional
    public DriverProfileResponse updateDriverProfile(UserPrincipal currentUser, UpdateDriverProfileRequest request) {
        User user = getUserOrThrow(currentUser.getUuid());
        DriverProfile driver = getOrCreateDriverProfile(user);

        // Validation: License dates
        if (request.getLicenseIssueDate() != null && request.getLicenseExpiryDate() != null) {
            if (!request.getLicenseExpiryDate().isAfter(request.getLicenseIssueDate())) {
                throw new BadRequestException("Driving license expiry date must be later than the issue date");
            }
        }

        // Validation: Age >= 18
        if (request.getDateOfBirth() != null) {
            int age = Period.between(request.getDateOfBirth(), LocalDate.now()).getYears();
            if (age < 18) {
                throw new BadRequestException("Driver must be at least 18 years of age (current age: " + age + ")");
            }
        }

        // Check unique license number
        String cleanLicense = request.getLicenseNumber().trim().toUpperCase();
        if (driverProfileRepository.existsByLicenseNumberAndIdNot(cleanLicense, driver.getId())) {
            throw new DuplicateResourceException("Driving license number is already registered: " + cleanLicense);
        }

        // Update driver profile fields
        driver.setLicenseNumber(cleanLicense);
        driver.setLicenseExpiryDate(request.getLicenseExpiryDate());
        driver.setLicenseIssueDate(request.getLicenseIssueDate());
        if (StringUtils.hasText(request.getLicenseIssuingAuthority())) {
            driver.setLicenseIssuingAuthority(request.getLicenseIssuingAuthority().trim());
        }
        if (StringUtils.hasText(request.getLicenseIssuingState())) {
            driver.setLicenseIssuingState(request.getLicenseIssuingState().trim());
        }
        if (StringUtils.hasText(request.getLicenseIssuingCountry())) {
            driver.setLicenseIssuingCountry(request.getLicenseIssuingCountry().trim());
        }
        if (StringUtils.hasText(request.getLicenseClass())) {
            driver.setLicenseClass(request.getLicenseClass().trim());
        }
        driver.setDateOfBirth(request.getDateOfBirth());
        driver.setResidentialAddress(request.getResidentialAddress().trim());
        driver.setCity(request.getCity().trim());
        driver.setState(request.getState().trim());
        driver.setPostalCode(request.getPostalCode().trim());
        if (StringUtils.hasText(request.getCountry())) {
            driver.setCountry(request.getCountry().trim());
        }
        if (StringUtils.hasText(request.getPreferredLanguage())) {
            driver.setPreferredLanguage(request.getPreferredLanguage().trim());
        }

        // Update emergency contact if supplied
        if (request.getEmergencyContact() != null) {
            saveEmergencyContact(user, request.getEmergencyContact());
        }

        onboardingPolicyService.evaluateAndSyncDriverOnboardingStatus(driver);
        DriverProfile saved = driverProfileRepository.save(driver);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public DriverProfileResponse patchDriverProfile(UserPrincipal currentUser, PatchDriverProfileRequest request) {
        User user = getUserOrThrow(currentUser.getUuid());
        DriverProfile driver = getOrCreateDriverProfile(user);

        if (StringUtils.hasText(request.getLicenseNumber())) {
            String cleanLicense = request.getLicenseNumber().trim().toUpperCase();
            if (driverProfileRepository.existsByLicenseNumberAndIdNot(cleanLicense, driver.getId())) {
                throw new DuplicateResourceException("Driving license number is already registered: " + cleanLicense);
            }
            driver.setLicenseNumber(cleanLicense);
        }

        if (request.getLicenseExpiryDate() != null) {
            driver.setLicenseExpiryDate(request.getLicenseExpiryDate());
        }
        if (request.getLicenseIssueDate() != null) {
            driver.setLicenseIssueDate(request.getLicenseIssueDate());
        }
        if (driver.getLicenseIssueDate() != null && driver.getLicenseExpiryDate() != null) {
            if (!driver.getLicenseExpiryDate().isAfter(driver.getLicenseIssueDate())) {
                throw new BadRequestException("Driving license expiry date must be later than the issue date");
            }
        }

        if (StringUtils.hasText(request.getLicenseIssuingAuthority())) {
            driver.setLicenseIssuingAuthority(request.getLicenseIssuingAuthority().trim());
        }
        if (StringUtils.hasText(request.getLicenseIssuingState())) {
            driver.setLicenseIssuingState(request.getLicenseIssuingState().trim());
        }
        if (StringUtils.hasText(request.getLicenseIssuingCountry())) {
            driver.setLicenseIssuingCountry(request.getLicenseIssuingCountry().trim());
        }
        if (StringUtils.hasText(request.getLicenseClass())) {
            driver.setLicenseClass(request.getLicenseClass().trim());
        }

        if (request.getDateOfBirth() != null) {
            int age = Period.between(request.getDateOfBirth(), LocalDate.now()).getYears();
            if (age < 18) {
                throw new BadRequestException("Driver must be at least 18 years of age");
            }
            driver.setDateOfBirth(request.getDateOfBirth());
        }

        if (StringUtils.hasText(request.getResidentialAddress())) {
            driver.setResidentialAddress(request.getResidentialAddress().trim());
        }
        if (StringUtils.hasText(request.getCity())) {
            driver.setCity(request.getCity().trim());
        }
        if (StringUtils.hasText(request.getState())) {
            driver.setState(request.getState().trim());
        }
        if (StringUtils.hasText(request.getPostalCode())) {
            driver.setPostalCode(request.getPostalCode().trim());
        }
        if (StringUtils.hasText(request.getCountry())) {
            driver.setCountry(request.getCountry().trim());
        }
        if (StringUtils.hasText(request.getPreferredLanguage())) {
            driver.setPreferredLanguage(request.getPreferredLanguage().trim());
        }

        if (request.getEmergencyContact() != null) {
            saveEmergencyContact(user, request.getEmergencyContact());
        }

        onboardingPolicyService.evaluateAndSyncDriverOnboardingStatus(driver);
        DriverProfile saved = driverProfileRepository.save(driver);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DriverOnboardingResponse getOnboardingStatus(UserPrincipal currentUser) {
        User user = getUserOrThrow(currentUser.getUuid());
        DriverProfile driver = getOrCreateDriverProfile(user);
        return onboardingPolicyService.calculateOnboardingProgress(driver);
    }

    @Override
    @Transactional
    public DriverOnboardingResponse submitForReview(UserPrincipal currentUser) {
        User user = getUserOrThrow(currentUser.getUuid());
        DriverProfile driver = getOrCreateDriverProfile(user);

        DriverOnboardingResponse progress = onboardingPolicyService.calculateOnboardingProgress(driver);
        if (!progress.isCanSubmitForReview()) {
            throw new BadRequestException("Cannot submit profile for review: prerequisites not satisfied: " + progress.getOutstandingTasks());
        }

        String previousStatus = driver.getOnboardingStatus().name();
        driver.setOnboardingStatus(OnboardingStatus.PENDING_REVIEW);
        driver.setRejectionReason(null);
        DriverProfile saved = driverProfileRepository.save(driver);

        verificationHistoryRepository.save(DriverVerificationHistory.builder()
                .driver(saved)
                .document(null)
                .actionType("PROFILE_SUBMISSION")
                .previousStatus(previousStatus)
                .newStatus(OnboardingStatus.PENDING_REVIEW.name())
                .reviewedByAdmin(user)
                .reason("Driver submitted completed profile and required documents for review")
                .build());

        return onboardingPolicyService.calculateOnboardingProgress(saved);
    }

    @Override
    @Transactional
    public DriverAvailabilityResponse updateAvailability(UserPrincipal currentUser, DriverAvailabilityRequest request) {
        User user = getUserOrThrow(currentUser.getUuid());
        DriverProfile driver = getOrCreateDriverProfile(user);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("Account is not active (" + user.getStatus() + "). Cannot change availability.");
        }

        boolean requestedOnline = false;
        if (request.getIsOnline() != null) {
            requestedOnline = request.getIsOnline();
        } else if (request.getStatus() != null) {
            requestedOnline = (request.getStatus() == DriverStatus.ONLINE);
        } else {
            throw new BadRequestException("Must specify desired status or isOnline flag");
        }

        if (requestedOnline) {
            if (driver.getOnboardingStatus() != OnboardingStatus.VERIFIED || !driver.isVerified()) {
                throw new ForbiddenException("Driver cannot go online: Onboarding is not fully VERIFIED.");
            }
            if (driver.getStatus() == DriverStatus.SUSPENDED) {
                throw new ForbiddenException("Driver is SUSPENDED and cannot go online.");
            }
            driver.setStatus(DriverStatus.ONLINE);
        } else {
            driver.setStatus(DriverStatus.OFFLINE);
        }

        driverProfileRepository.save(driver);

        return DriverAvailabilityResponse.builder()
                .driverUuid(user.getUuid())
                .status(driver.getStatus())
                .isOnline(driver.getStatus() == DriverStatus.ONLINE)
                .message("Driver availability updated to " + driver.getStatus())
                .build();
    }

    @Override
    @Transactional
    public DriverProfile getOrCreateDriverProfile(User user) {
        return driverProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    DriverProfile newProfile = DriverProfile.builder()
                            .user(user)
                            .status(DriverStatus.OFFLINE)
                            .rating(new BigDecimal("5.00"))
                            .totalTrips(0)
                            .acceptanceRate(new BigDecimal("100.00"))
                            .cancellationRate(new BigDecimal("0.00"))
                            .isVerified(false)
                            .onboardingStatus(OnboardingStatus.INCOMPLETE)
                            .country("India")
                            .licenseIssuingCountry("India")
                            .licenseClass("LMV")
                            .preferredLanguage("English")
                            .build();
                    return driverProfileRepository.save(newProfile);
                });
    }

    @Override
    public DriverProfileResponse mapToResponse(DriverProfile driver) {
        User user = driver.getUser();
        EmergencyContactDto emergencyContactDto = null;
        if (user != null) {
            Optional<EmergencyContact> contactOpt = emergencyContactRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
            if (contactOpt.isPresent()) {
                EmergencyContact ec = contactOpt.get();
                emergencyContactDto = EmergencyContactDto.builder()
                        .contactName(ec.getContactName())
                        .contactPhone(ec.getContactPhone())
                        .relationship(ec.getRelationship())
                        .build();
            }
        }

        return DriverProfileResponse.builder()
                .driverId(driver.getId())
                .userUuid(user != null ? user.getUuid() : null)
                .firstName(user != null ? user.getFirstName() : null)
                .lastName(user != null ? user.getLastName() : null)
                .email(user != null ? user.getEmail() : null)
                .phoneNumber(user != null ? user.getPhoneNumber() : null)
                .profilePictureUrl(user != null ? user.getProfilePictureUrl() : null)
                .licenseNumber(driver.getLicenseNumber())
                .licenseExpiryDate(driver.getLicenseExpiryDate())
                .licenseIssueDate(driver.getLicenseIssueDate())
                .licenseIssuingAuthority(driver.getLicenseIssuingAuthority())
                .licenseIssuingState(driver.getLicenseIssuingState())
                .licenseIssuingCountry(driver.getLicenseIssuingCountry())
                .licenseClass(driver.getLicenseClass())
                .dateOfBirth(driver.getDateOfBirth())
                .residentialAddress(driver.getResidentialAddress())
                .city(driver.getCity())
                .state(driver.getState())
                .postalCode(driver.getPostalCode())
                .country(driver.getCountry())
                .preferredLanguage(driver.getPreferredLanguage())
                .status(driver.getStatus())
                .rating(driver.getRating())
                .totalTrips(driver.getTotalTrips())
                .acceptanceRate(driver.getAcceptanceRate())
                .cancellationRate(driver.getCancellationRate())
                .isVerified(driver.isVerified())
                .verifiedAt(driver.getVerifiedAt())
                .backgroundCheckStatus(driver.getBackgroundCheckStatus())
                .onboardingStatus(driver.getOnboardingStatus())
                .rejectionReason(driver.getRejectionReason())
                .emergencyContact(emergencyContactDto)
                .createdAt(driver.getCreatedAt())
                .updatedAt(driver.getUpdatedAt())
                .build();
    }

    private User getUserOrThrow(String uuid) {
        return userRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with UUID: " + uuid));
    }

    private void saveEmergencyContact(User user, EmergencyContactDto dto) {
        Optional<EmergencyContact> existing = emergencyContactRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        EmergencyContact contact = existing.orElseGet(() -> EmergencyContact.builder().user(user).build());
        contact.setContactName(dto.getContactName().trim());
        contact.setContactPhone(dto.getContactPhone().trim());
        contact.setRelationship(StringUtils.hasText(dto.getRelationship()) ? dto.getRelationship().trim() : null);
        emergencyContactRepository.save(contact);
    }
}
