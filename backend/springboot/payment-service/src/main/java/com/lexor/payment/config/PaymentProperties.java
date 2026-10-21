package com.lexor.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Data
@Configuration
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

    private String provider = "RAZORPAY";
    private BigDecimal commissionPercentage = BigDecimal.valueOf(20.00);
    private String currency = "INR";
    private Razorpay razorpay = new Razorpay();

    @Data
    public static class Razorpay {
        private String keyId = "rzp_test_sample_key_123456";
        private String keySecret = "sample_razorpay_secret_654321";
        private String webhookSecret = "sample_webhook_secret_987654";
    }
}
