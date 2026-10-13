package com.lexor.vehicle.config;

import com.lexor.vehicle.entity.VehicleDocumentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class VehicleVerificationPolicyConfig {

    private final List<VehicleDocumentType> requiredDocuments;

    public VehicleVerificationPolicyConfig(
            @Value("${lexor.verification.required-documents:REGISTRATION_CERTIFICATE,VEHICLE_INSURANCE,POLLUTION_UNDER_CONTROL}") String requiredDocs
    ) {
        this.requiredDocuments = Arrays.stream(requiredDocs.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(VehicleDocumentType::valueOf)
                .collect(Collectors.toList());
    }

    public List<VehicleDocumentType> getRequiredDocuments() {
        return requiredDocuments;
    }
}
