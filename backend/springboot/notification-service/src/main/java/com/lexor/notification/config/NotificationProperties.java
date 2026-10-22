package com.lexor.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "lexor")
@Getter
@Setter
public class NotificationProperties {

    private Jwt jwt = new Jwt();
    private Security security = new Security();
    private Notification notification = new Notification();

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long expirationMs = 86400000;
    }

    @Getter
    @Setter
    public static class Security {
        private String internalServiceKey;
    }

    @Getter
    @Setter
    public static class Notification {
        private Providers providers = new Providers();
        private Limits limits = new Limits();
        private Templates templates = new Templates();
    }

    @Getter
    @Setter
    public static class Providers {
        private Fcm fcm = new Fcm();
        private Sms sms = new Sms();
        private Email email = new Email();
    }

    @Getter
    @Setter
    public static class Fcm {
        private boolean enabled = true;
        private String serverKey = "fcm_test_server_key_sandbox";
        private String projectId = "lexor-mobility-sandbox";
    }

    @Getter
    @Setter
    public static class Sms {
        private boolean enabled = true;
        private String provider = "TWILIO_SANDBOX";
        private String accountSid = "AC_test_lexor_sid";
        private String authToken = "auth_token_test_sandbox";
        private String fromNumber = "+15005550006";
        private int rateLimitPerHour = 5;
    }

    @Getter
    @Setter
    public static class Email {
        private boolean enabled = true;
        private String provider = "SMTP_SANDBOX";
        private String fromAddress = "no-reply@lexor.com";
        private String fromName = "LEXOR Mobility";
    }

    @Getter
    @Setter
    public static class Limits {
        private int otpRateLimitSeconds = 60;
        private int maxInboxPageSize = 50;
    }

    @Getter
    @Setter
    public static class Templates {
        private String defaultLocale = "en";
    }
}
