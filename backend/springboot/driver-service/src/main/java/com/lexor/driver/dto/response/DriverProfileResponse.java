package com.lexor.driver.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lexor.driver.dto.EmergencyContactDto;
import com.lexor.driver.entity.BackgroundCheckStatus;
import com.lexor.driver.entity.DriverStatus;
import com.lexor.driver.entity.OnboardingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DriverProfileResponse {

    private Long driverId;
    private String userUuid;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String profilePictureUrl;

    // Driving licence details
    private String licenseNumber;
    private LocalDate licenseExpiryDate;
    private LocalDate licenseIssueDate;
    private String licenseIssuingAuthority;
    private String licenseIssuingState;
    private String licenseIssuingCountry;
    private String licenseClass;

    // Personal & Address details
    private LocalDate dateOfBirth;
    private String residentialAddress;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private String preferredLanguage;

    // Availability and Operational metrics
    private DriverStatus status;
    private BigDecimal rating;
    private Integer totalTrips;
    private BigDecimal acceptanceRate;
    private BigDecimal cancellationRate;

    // Verification and Compliance
    private boolean isVerified;
    private LocalDateTime verifiedAt;
    private BackgroundCheckStatus backgroundCheckStatus;
    private OnboardingStatus onboardingStatus;
    private String rejectionReason;

    // Emergency Contact
    private EmergencyContactDto emergencyContact;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
