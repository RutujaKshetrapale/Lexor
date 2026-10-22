package com.lexor.feedback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "feedback")
@Data
public class FeedbackProperties {

    private Policy policy = new Policy();
    private Jwt jwt = new Jwt();
    private Security security = new Security();
    private Services services = new Services();

    @Data
    public static class Policy {
        private int editWindowMinutes = 120;
        private int submissionWindowDays = 7;
        private boolean editEnabled = true;
        private int reportAutoReviewThreshold = 3;
    }

    @Data
    public static class Jwt {
        private String secret;
        private long expirationMs = 86400000;
    }

    @Data
    public static class Security {
        private String internalServiceKey;
    }

    @Data
    public static class Services {
        private String bookingUrl = "http://localhost:8095";
        private String notificationUrl = "http://localhost:8099";
    }
}
