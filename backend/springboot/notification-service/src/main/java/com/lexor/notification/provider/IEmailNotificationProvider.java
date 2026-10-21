package com.lexor.notification.provider;

import com.lexor.notification.provider.dto.ProviderDispatchResult;

public interface IEmailNotificationProvider {

    String getProviderName();

    ProviderDispatchResult sendEmail(String toAddress, String subject, String bodyHtml, String bodyText);
}
