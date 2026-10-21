package com.lexor.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpDeliveryRequest {

    private String recipientPhone;
    private String recipientEmail;

    @NotBlank(message = "OTP code is required")
    private String otpCode;

    @NotBlank(message = "Purpose is required")
    private String purpose; // e.g. AUTH_LOGIN, PASSWORD_RESET, RIDE_START

    @Builder.Default
    private int expirySeconds = 300;

    @Override
    public String toString() {
        // Strict redaction of plaintext OTP in toString/logging
        return "OtpDeliveryRequest(recipientPhone=" + maskPhone(recipientPhone)
                + ", recipientEmail=" + maskEmail(recipientEmail)
                + ", otpCode=[REDACTED]"
                + ", purpose=" + purpose
                + ", expirySeconds=" + expirySeconds + ")";
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 2);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@");
        return parts[0].charAt(0) + "***@" + parts[1];
    }
}
