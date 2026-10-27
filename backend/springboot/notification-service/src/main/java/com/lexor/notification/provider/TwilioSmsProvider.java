package com.lexor.notification.provider;

import com.lexor.notification.config.NotificationProperties;
import com.lexor.notification.provider.dto.ProviderDispatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Twilio SMS Provider Integration
 * Extensible provider for global SMS and OTP dispatch.
 */
@Component("twilioSmsProvider")
@RequiredArgsConstructor
@Slf4j
public class TwilioSmsProvider implements ISmsNotificationProvider {

    private final NotificationProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    // In-memory sliding rate limit tracker per destination number
    private final Map<String, Long> lastSentTimestampByPhone = new ConcurrentHashMap<>();

    @Override
    public String getProviderName() {
        return isLiveMode() ? "TWILIO" : "TWILIO_SANDBOX";
    }

    public boolean isLiveMode() {
        var smsProps = properties.getNotification().getProviders().getSms();
        String provider = smsProps.getProvider();
        String sid = smsProps.getAccountSid();
        String token = smsProps.getAuthToken();
        return smsProps.isEnabled()
                && provider != null
                && !provider.toUpperCase().contains("SANDBOX")
                && StringUtils.hasText(sid)
                && sid.startsWith("AC")
                && !sid.contains("test")
                && StringUtils.hasText(token)
                && !token.contains("placeholder");
    }

    @Override
    public ProviderDispatchResult sendSms(String destinationPhone, String messageBody) {
        var smsProps = properties.getNotification().getProviders().getSms();
        if (!smsProps.isEnabled()) {
            log.info("Twilio SMS provider disabled in configuration, skipping dispatch");
            return ProviderDispatchResult.accepted("SMS_DISABLED_" + UUID.randomUUID());
        }

        if (destinationPhone == null || destinationPhone.trim().isEmpty()) {
            return ProviderDispatchResult.failure("INVALID_DESTINATION", "Destination phone cannot be empty", false);
        }

        if (isLiveMode()) {
            return executeLiveDispatch(destinationPhone, messageBody);
        }

        // Sandbox / Isolated Test Mode Execution
        String sid = "SM" + UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        lastSentTimestampByPhone.put(destinationPhone, System.currentTimeMillis());

        log.info("Submitted SMS via {} [sid={}] to phone={}",
                getProviderName(), sid, maskPhone(destinationPhone));

        return ProviderDispatchResult.accepted(sid);
    }

    private ProviderDispatchResult executeLiveDispatch(String destinationPhone, String messageBody) {
        var smsProps = properties.getNotification().getProviders().getSms();
        String accountSid = smsProps.getAccountSid();
        String authToken = smsProps.getAuthToken();
        String fromNumber = smsProps.getFromNumber();

        String url = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";
        int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                headers.setBasicAuth(accountSid, authToken);

                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add("To", destinationPhone);
                form.add("From", fromNumber);
                form.add("Body", messageBody);

                HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
                ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    String sid = (String) response.getBody().get("sid");
                    lastSentTimestampByPhone.put(destinationPhone, System.currentTimeMillis());
                    log.info("Successfully dispatched live Twilio SMS [sid={}] to phone={}",
                            sid, maskPhone(destinationPhone));
                    return ProviderDispatchResult.accepted(sid != null ? sid : "SM_TWILIO_OK");
                }
            } catch (HttpStatusCodeException ex) {
                log.warn("Twilio API error on attempt {}/{} [status={}]: {}",
                        attempt, maxAttempts, ex.getStatusCode(), ex.getStatusText());
                if (ex.getStatusCode().is4xxClientError()) {
                    return ProviderDispatchResult.failure(
                            "TWILIO_REJECTED_" + ex.getStatusCode().value(),
                            "Provider rejected SMS request: " + ex.getStatusText(),
                            false
                    );
                }
            } catch (ResourceAccessException ex) {
                log.warn("Twilio network timeout on attempt {}/{}: {}", attempt, maxAttempts, ex.getMessage());
            } catch (Exception ex) {
                log.error("Unexpected error dispatching Twilio SMS: {}", ex.getMessage());
            }

            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(500L * attempt);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return ProviderDispatchResult.failure(
                "TWILIO_OUTAGE",
                "Twilio SMS provider dispatch failed after " + maxAttempts + " attempts",
                true
        );
    }

    public String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 2);
    }
}
