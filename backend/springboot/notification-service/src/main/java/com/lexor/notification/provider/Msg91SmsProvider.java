package com.lexor.notification.provider;

import com.lexor.notification.config.NotificationProperties;
import com.lexor.notification.provider.dto.ProviderDispatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MSG91 SMS & Ride-Start OTP Provider Integration
 * Authoritative provider for Indian telecom compliance, DLT template routing,
 * and high-deliverability SMS dispatch.
 */
@Component("msg91SmsProvider")
@RequiredArgsConstructor
@Slf4j
public class Msg91SmsProvider implements ISmsNotificationProvider {

    private static final String MSG91_OTP_API_URL = "https://control.msg91.com/api/v5/otp";
    private static final String MSG91_FLOW_API_URL = "https://control.msg91.com/api/v5/flow/";

    private final NotificationProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    // In-memory sliding rate limit tracker per destination number: phone -> epochMillis
    private final Map<String, Long> lastSentTimestampByPhone = new ConcurrentHashMap<>();

    @Override
    public String getProviderName() {
        return isLiveMode() ? "MSG91" : "MSG91_SANDBOX";
    }

    public boolean isLiveMode() {
        var smsProps = properties.getNotification().getProviders().getSms();
        String provider = smsProps.getProvider();
        String authKey = smsProps.getMsg91().getAuthKey();
        return smsProps.isEnabled()
                && provider != null
                && !provider.toUpperCase().contains("SANDBOX")
                && StringUtils.hasText(authKey)
                && !authKey.startsWith("your_")
                && !authKey.contains("placeholder");
    }

    @Override
    public ProviderDispatchResult sendSms(String destinationPhone, String messageBody) {
        var smsProps = properties.getNotification().getProviders().getSms();
        if (!smsProps.isEnabled()) {
            log.info("MSG91 SMS provider disabled in configuration, skipping dispatch");
            return ProviderDispatchResult.accepted("MSG91_DISABLED_" + UUID.randomUUID());
        }

        if (destinationPhone == null || destinationPhone.trim().isEmpty()) {
            return ProviderDispatchResult.failure("INVALID_DESTINATION", "Destination phone cannot be empty", false);
        }

        String normalizedPhone = normalizePhoneNumber(destinationPhone);
        if (normalizedPhone.length() < 10) {
            return ProviderDispatchResult.failure("MALFORMED_PHONE", "Destination phone must contain at least 10 digits", false);
        }

        // Enforce provider rate limit cooldown per phone
        long now = System.currentTimeMillis();
        Long lastSent = lastSentTimestampByPhone.get(normalizedPhone);
        long cooldownMs = properties.getNotification().getLimits().getOtpRateLimitSeconds() * 1000L;
        if (lastSent != null && (now - lastSent) < cooldownMs) {
            long remaining = (lastSent + cooldownMs - now) / 1000L;
            return ProviderDispatchResult.failure(
                    "RATE_LIMIT_COOLDOWN",
                    "SMS resend cooldown active for " + remaining + " seconds",
                    false
            );
        }

        if (isLiveMode()) {
            return executeLiveDispatchWithRetry(normalizedPhone, messageBody);
        }

        // Sandbox / Isolated Test Mode Execution
        String simulatedReqId = "msg91_req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        lastSentTimestampByPhone.put(normalizedPhone, now);

        log.info("Submitted SMS via {} [reqId={}] to phone={}",
                getProviderName(), simulatedReqId, maskPhone(destinationPhone));

        return ProviderDispatchResult.accepted(simulatedReqId);
    }

    private ProviderDispatchResult executeLiveDispatchWithRetry(String normalizedPhone, String messageBody) {
        var msg91Props = properties.getNotification().getProviders().getSms().getMsg91();
        int maxAttempts = 3;
        long backoffMs = 500;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("authkey", msg91Props.getAuthKey());

                // Build MSG91 API payload
                Map<String, Object> payload;
                String endpoint;
                if (StringUtils.hasText(msg91Props.getTemplateId())) {
                    endpoint = MSG91_FLOW_API_URL;
                    payload = Map.of(
                            "flow_id", msg91Props.getTemplateId(),
                            "sender", StringUtils.hasText(msg91Props.getSenderId()) ? msg91Props.getSenderId() : "LEXORS",
                            "mobiles", normalizedPhone,
                            "var1", messageBody
                    );
                } else {
                    endpoint = MSG91_OTP_API_URL;
                    payload = Map.of(
                            "mobile", normalizedPhone,
                            "template_id", msg91Props.getTemplateId() != null ? msg91Props.getTemplateId() : "",
                            "otp_length", 4,
                            "otp_expiry", msg91Props.getOtpExpiryMinutes()
                    );
                }

                HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);
                ResponseEntity<Map> response = restTemplate.exchange(endpoint, HttpMethod.POST, requestEntity, Map.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    String reqId = "msg91_" + UUID.randomUUID().toString().substring(0, 8);
                    if (response.getBody() != null && response.getBody().get("request_id") != null) {
                        reqId = response.getBody().get("request_id").toString();
                    } else if (response.getBody() != null && response.getBody().get("message") != null) {
                        reqId = response.getBody().get("message").toString();
                    }
                    lastSentTimestampByPhone.put(normalizedPhone, System.currentTimeMillis());
                    log.info("Successfully dispatched live SMS via MSG91 [reqId={}] to phone={}",
                            reqId, maskPhone(normalizedPhone));
                    return ProviderDispatchResult.accepted(reqId);
                }
            } catch (HttpStatusCodeException ex) {
                log.warn("MSG91 API error on attempt {}/{} [status={}]: {}",
                        attempt, maxAttempts, ex.getStatusCode(), ex.getStatusText());
                if (ex.getStatusCode().is4xxClientError()) {
                    // Do not retry 4xx client errors (bad auth, invalid template, invalid mobile)
                    return ProviderDispatchResult.failure(
                            "MSG91_REJECTED_" + ex.getStatusCode().value(),
                            "Provider rejected request: " + ex.getStatusText(),
                            false
                    );
                }
            } catch (ResourceAccessException ex) {
                log.warn("MSG91 network timeout on attempt {}/{}: {}", attempt, maxAttempts, ex.getMessage());
            } catch (Exception ex) {
                log.error("Unexpected error dispatching MSG91 SMS on attempt {}/{}: {}",
                        attempt, maxAttempts, ex.getMessage());
            }

            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(backoffMs * attempt);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return ProviderDispatchResult.failure(
                "MSG91_OUTAGE",
                "MSG91 SMS provider dispatch failed after " + maxAttempts + " attempts",
                true
        );
    }

    public String normalizePhoneNumber(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 10) {
            return "91" + digits;
        }
        return digits;
    }

    public String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) return "***";
        String clean = phone.replaceAll("\\s+", "");
        return clean.substring(0, Math.min(3, clean.length()))
                + "****"
                + (clean.length() > 5 ? clean.substring(clean.length() - 2) : "");
    }
}
