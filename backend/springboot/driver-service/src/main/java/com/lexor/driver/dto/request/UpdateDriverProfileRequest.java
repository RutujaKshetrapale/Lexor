package com.lexor.driver.dto.request;

import com.lexor.driver.dto.EmergencyContactDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDriverProfileRequest {

    @NotBlank(message = "License number is required")
    @Size(max = 50, message = "License number cannot exceed 50 characters")
    private String licenseNumber;

    @NotNull(message = "License expiry date is required")
    @Future(message = "License expiry date must be in the future")
    private LocalDate licenseExpiryDate;

    @PastOrPresent(message = "License issue date must be in the past or present")
    private LocalDate licenseIssueDate;

    @Size(max = 100, message = "Issuing authority cannot exceed 100 characters")
    private String licenseIssuingAuthority;

    @Size(max = 100, message = "Issuing state cannot exceed 100 characters")
    private String licenseIssuingState;

    @Size(max = 100, message = "Issuing country cannot exceed 100 characters")
    private String licenseIssuingCountry;

    @Size(max = 50, message = "License class cannot exceed 50 characters")
    private String licenseClass;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @NotBlank(message = "Residential address is required")
    @Size(max = 255, message = "Residential address cannot exceed 255 characters")
    private String residentialAddress;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City cannot exceed 100 characters")
    private String city;

    @NotBlank(message = "State is required")
    @Size(max = 100, message = "State cannot exceed 100 characters")
    private String state;

    @NotBlank(message = "Postal code is required")
    @Size(max = 20, message = "Postal code cannot exceed 20 characters")
    private String postalCode;

    @Size(max = 100, message = "Country cannot exceed 100 characters")
    private String country;

    @Size(max = 50, message = "Preferred language cannot exceed 50 characters")
    private String preferredLanguage;

    @Valid
    private EmergencyContactDto emergencyContact;
}
