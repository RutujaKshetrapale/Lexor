package com.lexor.notification.service.impl;

import com.lexor.notification.config.NotificationProperties;
import com.lexor.notification.dto.request.OtpDeliveryRequest;
import com.lexor.notification.dto.response.ApiResponse;
import com.lexor.notification.exception.BadRequestException;
import com.lexor.notification.exception.RateLimitExceededException;
import com.lexor.notification.provider.IEmailNotificationProvider;
import com.lexor.notification.provider.ISmsNotificationProvider;
import com.lexor.notification.provider.dto.ProviderDispatchResult;
import com.lexor.notification.service.IOtpDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpDeliveryServiceImpl implements IOtpDeliveryService {

    private final ISmsNotificationProvider smsProvider;
    private final IEmailNotificationProvider emailProvider;
    private final NotificationProperties properties;

    // In-memory sliding rate limit tracker per destination: Destination -> Timestamp
    private final Map<String, Long> rateLimitTracker = new ConcurrentHashMap<>();

    @Override
    public ApiResponse<String> deliverOtp(OtpDeliveryRequest request) {
        String phone = request.getRecipientPhone();
        String email = request.getRecipientEmail();

        if ((phone == null || phone.trim().isEmpty()) && (email == null || email.trim().isEmpty())) {
            throw new BadRequestException("Either recipientPhone or recipientEmail must be provided");
        }

        String primaryDestination = (phone != null && !phone.trim().isEmpty()) ? phone.trim() : email.trim();

        // Check Rate Limit (default 60 seconds per destination)
        long minIntervalMs = properties.getNotification().getLimits().getOtpRateLimitSeconds() * 1000L;
        long now = System.currentTimeMillis();
        Long lastSent = rateLimitTracker.get(primaryDestination);

        if (lastSent != null && (now - lastSent) < minIntervalMs) {
            long waitSeconds = ((lastSent + minIntervalMs) - now) / 1000L;
            log.warn("Rate limit triggered for OTP delivery to destination: {}", maskDestination(primaryDestination));
            throw new RateLimitExceededException("Please wait " + waitSeconds + " seconds before requesting another OTP");
        }

        // Deliver via SMS if phone is present
        if (phone != null && !phone.trim().isEmpty()) {
            String smsBody = "LEXOR: Your verification OTP is " + request.getOtpCode()
                    + ". Valid for " + (request.getExpirySeconds() / 60) + " minutes. Do NOT share with anyone.";
            ProviderDispatchResult result = smsProvider.sendSms(phone, smsBody);
            rateLimitTracker.put(phone, now);
            log.info("Dispatched OTP via SMS to {} with status {}", maskDestination(phone), result.getStatus());
        }

        // Deliver via Email if email is present
        if (email != null && !email.trim().isEmpty()) {
            String subject = "Your LEXOR Verification Code";
            String emailText = "Hello,\n\nYour LEXOR verification OTP is: " + request.getOtpCode()
                    + "\n\nThis OTP is valid for " + (request.getExpirySeconds() / 60)
                    + " minutes. Never share this code with anyone.\n\nBest regards,\nLEXOR Mobility Team";
            String emailHtml = "<p>Hello,</p><p>Your LEXOR verification OTP is: <strong>"
                    + request.getOtpCode() + "</strong></p><p>Valid for " + (request.getExpirySeconds() / 60)
                    + " minutes.</p>";
            ProviderDispatchResult result = emailProvider.sendEmail(email, subject, emailHtml, emailText);
            rateLimitTracker.put(email, now);
            log.info("Dispatched OTP via Email to {} with status {}", maskDestination(email), result.getStatus());
        }

        return ApiResponse.success("OTP dispatched successfully to destination");
    }

    private String maskDestination(String dest) {
        if (dest == null || dest.length() < 5) return "***";
        if (dest.contains("@")) {
            String[] parts = dest.split("@");
            return parts[0].charAt(0) + "***@" + parts[1];
        }
        return dest.substring(0, 3) + "****" + dest.substring(dest.length() - 2);
    }
}
