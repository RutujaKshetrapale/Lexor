package com.lexor.driver.dto.request;

import com.lexor.driver.dto.EmergencyContactDto;
import jakarta.validation.Valid;
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
public class PatchDriverProfileRequest {

    @Size(max = 50, message = "License number cannot exceed 50 characters")
    private String licenseNumber;

    private LocalDate licenseExpiryDate;

    private LocalDate licenseIssueDate;

    @Size(max = 100, message = "Issuing authority cannot exceed 100 characters")
    private String licenseIssuingAuthority;

    @Size(max = 100, message = "Issuing state cannot exceed 100 characters")
    private String licenseIssuingState;

    @Size(max = 100, message = "Issuing country cannot exceed 100 characters")
    private String licenseIssuingCountry;

    @Size(max = 50, message = "License class cannot exceed 50 characters")
    private String licenseClass;

    private LocalDate dateOfBirth;

    @Size(max = 255, message = "Residential address cannot exceed 255 characters")
    private String residentialAddress;

    @Size(max = 100, message = "City cannot exceed 100 characters")
    private String city;

    @Size(max = 100, message = "State cannot exceed 100 characters")
    private String state;

    @Size(max = 20, message = "Postal code cannot exceed 20 characters")
    private String postalCode;

    @Size(max = 100, message = "Country cannot exceed 100 characters")
    private String country;

    @Size(max = 50, message = "Preferred language cannot exceed 50 characters")
    private String preferredLanguage;

    @Valid
    private EmergencyContactDto emergencyContact;
}
