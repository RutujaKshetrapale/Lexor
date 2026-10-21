package com.lexor.notification.provider.dto;

import com.lexor.notification.enums.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderDispatchResult {
    private boolean successful;
    private DeliveryStatus status;
    private String providerMessageId;
    private String errorCode;
    private String errorMessage;
    private boolean shouldRetry;

    public static ProviderDispatchResult success(String providerMessageId) {
        return ProviderDispatchResult.builder()
                .successful(true)
                .status(DeliveryStatus.DELIVERED)
                .providerMessageId(providerMessageId)
                .build();
    }

    public static ProviderDispatchResult accepted(String providerMessageId) {
        return ProviderDispatchResult.builder()
                .successful(true)
                .status(DeliveryStatus.ACCEPTED)
                .providerMessageId(providerMessageId)
                .build();
    }

    public static ProviderDispatchResult failure(String errorCode, String errorMessage, boolean shouldRetry) {
        return ProviderDispatchResult.builder()
                .successful(false)
                .status(shouldRetry ? DeliveryStatus.RETRY_SCHEDULED : DeliveryStatus.FAILED)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .shouldRetry(shouldRetry)
                .build();
    }
}
