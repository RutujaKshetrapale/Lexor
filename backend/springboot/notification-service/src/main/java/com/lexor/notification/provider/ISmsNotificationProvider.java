package com.lexor.notification.provider;

import com.lexor.notification.provider.dto.ProviderDispatchResult;

public interface ISmsNotificationProvider {

    String getProviderName();

    ProviderDispatchResult sendSms(String destinationPhone, String messageBody);
}
