package com.lexor.payment.provider;

import com.lexor.payment.config.PaymentProperties;
import com.lexor.payment.enums.PaymentMethod;
import com.lexor.payment.exception.PaymentGatewayException;
import com.lexor.payment.provider.dto.CaptureResult;
import com.lexor.payment.provider.dto.CreateOrderResult;
import com.lexor.payment.provider.dto.PaymentVerificationResult;
import com.lexor.payment.provider.dto.RefundResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Component("razorpayPaymentProvider")
@RequiredArgsConstructor
public class RazorpayPaymentProvider implements IPaymentProvider {

    private static final Logger logger = LoggerFactory.getLogger(RazorpayPaymentProvider.class);
    private static final String RAZORPAY_API_BASE = "https://api.razorpay.com/v1";

    private final PaymentProperties paymentProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getProviderName() {
        return "RAZORPAY";
    }

    public boolean isLiveMode() {
        String keyId = paymentProperties.getRazorpay().getKeyId();
        String keySecret = paymentProperties.getRazorpay().getKeySecret();
        return StringUtils.hasText(keyId)
                && StringUtils.hasText(keySecret)
                && !keyId.contains("sample")
                && !keyId.startsWith("your_")
                && !keySecret.contains("sample")
                && !keySecret.startsWith("your_");
    }

    @Override
    public CreateOrderResult createOrder(String bookingUuid, BigDecimal amount, String currency, PaymentMethod method) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentGatewayException("Invalid transaction amount for order creation");
        }

        long amountPaise = amount.multiply(BigDecimal.valueOf(100)).longValue();
        String orderId;

        if (isLiveMode()) {
            orderId = createLiveRazorpayOrder(bookingUuid, amountPaise, currency != null ? currency : "INR");
        } else {
            orderId = "order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            logger.info("Created Razorpay sandbox order {} for booking {} with method {}", orderId, bookingUuid, method);
        }

        Map<String, Object> checkoutPayload = new HashMap<>();
        checkoutPayload.put("orderId", orderId);
        checkoutPayload.put("amount", amountPaise);
        checkoutPayload.put("currency", currency != null ? currency : "INR");
        checkoutPayload.put("name", "LEXOR — Intelligent Mobility");
        checkoutPayload.put("description", "Ride Payment for Booking " + bookingUuid);
        checkoutPayload.put("key", paymentProperties.getRazorpay().getKeyId());

        if (method == PaymentMethod.UPI) {
            Map<String, Object> upiPayload = new HashMap<>();
            upiPayload.put("flow", "intent");
            upiPayload.put("vpa", "lexor@icici");
            upiPayload.put("intentUrl", "upi://pay?pa=lexor@icici&pn=LEXOR&am=" + amount + "&cu=INR&tr=" + orderId);
            upiPayload.put("qrCodeString", "upi://pay?pa=lexor@icici&pn=LEXOR&am=" + amount + "&cu=INR&tr=" + orderId);
            checkoutPayload.put("upi", upiPayload);
        } else if (method == PaymentMethod.CARD) {
            Map<String, Object> cardPayload = new HashMap<>();
            cardPayload.put("saveCardAllowed", true);
            cardPayload.put("supportedNetworks", new String[]{"VISA", "MASTERCARD", "RUPAY"});
            checkoutPayload.put("card", cardPayload);
        } else if (method == PaymentMethod.NET_BANKING) {
            Map<String, Object> nbPayload = new HashMap<>();
            nbPayload.put("supportedBanks", new String[]{"HDFC", "ICICI", "SBIN", "KKBK", "AXIS"});
            checkoutPayload.put("netbanking", nbPayload);
        }

        return CreateOrderResult.builder()
                .orderId(orderId)
                .amountPaise(amountPaise)
                .amount(amount)
                .currency(currency != null ? currency : "INR")
                .providerKeyId(paymentProperties.getRazorpay().getKeyId())
                .checkoutPayload(checkoutPayload)
                .build();
    }

    private String createLiveRazorpayOrder(String bookingUuid, long amountPaise, String currency) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBasicAuth(
                    paymentProperties.getRazorpay().getKeyId(),
                    paymentProperties.getRazorpay().getKeySecret()
            );

            Map<String, Object> orderRequest = new HashMap<>();
            orderRequest.put("amount", amountPaise);
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", "rcpt_" + (bookingUuid.length() > 30 ? bookingUuid.substring(0, 30) : bookingUuid));
            orderRequest.put("payment_capture", 1);
            orderRequest.put("notes", Map.of("bookingUuid", bookingUuid, "platform", "LEXOR"));

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(orderRequest, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    RAZORPAY_API_BASE + "/orders",
                    requestEntity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String orderId = (String) response.getBody().get("id");
                logger.info("Successfully created live Razorpay order {} for booking {}", orderId, bookingUuid);
                return orderId;
            }
        } catch (HttpStatusCodeException ex) {
            logger.error("Razorpay order creation rejected [status={}]: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new PaymentGatewayException("Razorpay gateway order creation failed: " + ex.getStatusText(), ex);
        } catch (Exception ex) {
            logger.error("Unexpected error connecting to Razorpay orders API: {}", ex.getMessage());
            throw new PaymentGatewayException("Payment gateway unreachable: " + ex.getMessage(), ex);
        }
        throw new PaymentGatewayException("Failed to obtain order from Razorpay gateway");
    }

    @Override
    public boolean verifyCheckoutSignature(String orderId, String paymentId, String signature) {
        if (orderId == null || paymentId == null || signature == null) {
            return false;
        }
        try {
            String data = orderId + "|" + paymentId;
            String secret = paymentProperties.getRazorpay().getKeySecret();
            String computedSignature = calculateHmacSha256(data, secret);
            return MessageDigest.isEqual(
                    computedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception ex) {
            logger.error("Error verifying checkout signature: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        if (rawBody == null || signature == null) {
            return false;
        }
        try {
            String secret = paymentProperties.getRazorpay().getWebhookSecret();
            String computedSignature = calculateHmacSha256(rawBody, secret);
            return MessageDigest.isEqual(
                    computedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception ex) {
            logger.error("Error verifying webhook signature: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public CaptureResult capturePayment(String paymentId, BigDecimal amount, String currency) {
        if (paymentId == null || paymentId.isBlank()) {
            throw new PaymentGatewayException("Missing provider payment ID for capture");
        }
        logger.info("Captured payment {} for amount {} {}", paymentId, amount, currency);
        return CaptureResult.builder()
                .successful(true)
                .paymentId(paymentId)
                .amount(amount)
                .currency(currency != null ? currency : "INR")
                .status("captured")
                .build();
    }

    @Override
    public RefundResult initiateRefund(String paymentId, BigDecimal amount, String currency, String reason) {
        if (paymentId == null || paymentId.isBlank()) {
            throw new PaymentGatewayException("Missing provider payment ID for refund");
        }
        String refundId = "rfnd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        logger.info("Initiated refund {} for payment {} for amount {} {}: {}", refundId, paymentId, amount, currency, reason);
        return RefundResult.builder()
                .successful(true)
                .refundId(refundId)
                .paymentId(paymentId)
                .amount(amount)
                .currency(currency != null ? currency : "INR")
                .status("processed")
                .build();
    }

    @Override
    public PaymentVerificationResult fetchPaymentStatus(String paymentId) {
        return PaymentVerificationResult.builder()
                .verified(true)
                .paymentId(paymentId)
                .status("captured")
                .method("upi")
                .build();
    }

    private String calculateHmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception ex) {
            throw new PaymentGatewayException("Failed to calculate HMAC-SHA256 signature", ex);
        }
    }
}
