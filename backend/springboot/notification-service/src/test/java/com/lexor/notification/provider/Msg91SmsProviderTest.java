package com.lexor.notification.provider;

import com.lexor.notification.config.NotificationProperties;
import com.lexor.notification.provider.dto.ProviderDispatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Msg91SmsProviderTest {

    private Msg91SmsProvider provider;
    private NotificationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        properties.getNotification().getProviders().getSms().setEnabled(true);
        properties.getNotification().getProviders().getSms().setProvider("MSG91_SANDBOX");
        properties.getNotification().getProviders().getSms().getMsg91().setAuthKey("test_auth_key");
        properties.getNotification().getProviders().getSms().getMsg91().setTemplateId("test_tpl_id");
        properties.getNotification().getLimits().setOtpRateLimitSeconds(60);

        provider = new Msg91SmsProvider(properties);
    }

    @Test
    @DisplayName("Should normalize Indian phone numbers by prepending country code 91")
    void testNormalizePhoneNumber() {
        assertEquals("919876543210", provider.normalizePhoneNumber("9876543210"));
        assertEquals("919876543210", provider.normalizePhoneNumber("+91 98765 43210"));
        assertEquals("919876543210", provider.normalizePhoneNumber("+91-9876543210"));
        assertEquals("14155552671", provider.normalizePhoneNumber("+1 (415) 555-2671"));
    }

    @Test
    @DisplayName("Should mask phone number safely in logs")
    void testMaskPhone() {
        assertEquals("+91****10", provider.maskPhone("+919876543210"));
        assertEquals("***", provider.maskPhone("123"));
        assertEquals("***", provider.maskPhone(null));
    }

    @Test
    @DisplayName("Should reject empty or malformed destination phone")
    void testSendSmsEmptyPhone() {
        ProviderDispatchResult res = provider.sendSms("", "Your OTP is 4829");
        assertFalse(res.isSuccessful());
        assertEquals("INVALID_DESTINATION", res.getErrorCode());

        ProviderDispatchResult res2 = provider.sendSms("12345", "Your OTP is 4829");
        assertFalse(res2.isSuccessful());
        assertEquals("MALFORMED_PHONE", res2.getErrorCode());
    }

    @Test
    @DisplayName("Should accept valid SMS in sandbox mode and return MSG91 request id")
    void testSendSmsSandboxSuccess() {
        ProviderDispatchResult res = provider.sendSms("+919876543210", "Your OTP is 4829");
        assertTrue(res.isSuccessful());
        assertNotNull(res.getProviderMessageId());
        assertTrue(res.getProviderMessageId().startsWith("msg91_req_"));
        assertEquals("MSG91_SANDBOX", provider.getProviderName());
    }

    @Test
    @DisplayName("Should enforce resend cooldown on repeated rapid requests to same phone")
    void testSendSmsRateLimitCooldown() {
        ProviderDispatchResult first = provider.sendSms("+919876543211", "Your OTP is 4829");
        assertTrue(first.isSuccessful());

        ProviderDispatchResult second = provider.sendSms("+919876543211", "Your OTP is 4829");
        assertFalse(second.isSuccessful());
        assertEquals("RATE_LIMIT_COOLDOWN", second.getErrorCode());
    }
}
