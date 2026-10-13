package com.lexor.vehicle.service;

import com.lexor.vehicle.config.VehicleVerificationPolicyConfig;
import com.lexor.vehicle.entity.DocumentVerificationStatus;
import com.lexor.vehicle.entity.VehicleDocument;
import com.lexor.vehicle.entity.VehicleDocumentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VehicleVerificationPolicyService {

    private final VehicleVerificationPolicyConfig policyConfig;

    public List<VehicleDocumentType> getRequiredDocumentTypes() {
        return policyConfig.getRequiredDocuments();
    }

    public boolean hasAllRequiredDocumentsVerified(List<VehicleDocument> documents) {
        Set<VehicleDocumentType> verifiedTypes = documents.stream()
                .filter(VehicleDocument::isCurrent)
                .filter(d -> d.getVerificationStatus() == DocumentVerificationStatus.VERIFIED)
                .map(VehicleDocument::getDocumentType)
                .collect(Collectors.toSet());

        return verifiedTypes.containsAll(policyConfig.getRequiredDocuments());
    }

    public List<VehicleDocumentType> getMissingDocuments(List<VehicleDocument> documents) {
        Set<VehicleDocumentType> presentTypes = documents.stream()
                .filter(VehicleDocument::isCurrent)
                .map(VehicleDocument::getDocumentType)
                .collect(Collectors.toSet());

        List<VehicleDocumentType> missing = new ArrayList<>();
        for (VehicleDocumentType required : policyConfig.getRequiredDocuments()) {
            if (!presentTypes.contains(required)) {
                missing.add(required);
            }
        }
        return missing;
    }
}
