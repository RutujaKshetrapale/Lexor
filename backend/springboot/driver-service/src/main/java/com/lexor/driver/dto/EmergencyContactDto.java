package com.lexor.driver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmergencyContactDto {

    @NotBlank(message = "Emergency contact name is required")
    @Size(max = 100, message = "Emergency contact name cannot exceed 100 characters")
    private String contactName;

    @NotBlank(message = "Emergency contact phone is required")
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Emergency contact phone must be valid with 10 to 15 digits")
    private String contactPhone;

    @Size(max = 50, message = "Relationship cannot exceed 50 characters")
    private String relationship;
}
