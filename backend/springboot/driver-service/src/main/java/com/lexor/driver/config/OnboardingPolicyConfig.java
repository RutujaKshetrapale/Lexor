package com.lexor.driver.config;

import com.lexor.driver.entity.DocumentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Configuration
public class OnboardingPolicyConfig {

    private final List<DocumentType> requiredDocumentTypes;

    public OnboardingPolicyConfig(
            @Value("${lexor.onboarding.required-documents:DRIVING_LICENSE,GOVERNMENT_ID,ADDRESS_PROOF}") String configStr
    ) {
        List<DocumentType> list = new ArrayList<>();
        if (configStr != null && !configStr.trim().isEmpty()) {
            for (String item : configStr.split(",")) {
                try {
                    list.add(DocumentType.valueOf(item.trim().toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (list.isEmpty()) {
            list.add(DocumentType.DRIVING_LICENSE);
            list.add(DocumentType.GOVERNMENT_ID);
            list.add(DocumentType.ADDRESS_PROOF);
        }
        this.requiredDocumentTypes = Collections.unmodifiableList(list);
    }

    public List<DocumentType> getRequiredDocumentTypes() {
        return requiredDocumentTypes;
    }
}
