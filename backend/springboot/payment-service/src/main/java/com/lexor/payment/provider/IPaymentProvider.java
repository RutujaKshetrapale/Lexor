package com.lexor.payment.provider;

import com.lexor.payment.enums.PaymentMethod;
import com.lexor.payment.provider.dto.CaptureResult;
import com.lexor.payment.provider.dto.CreateOrderResult;
import com.lexor.payment.provider.dto.PaymentVerificationResult;
import com.lexor.payment.provider.dto.RefundResult;

import java.math.BigDecimal;

public interface IPaymentProvider {

    String getProviderName();

    CreateOrderResult createOrder(String bookingUuid, BigDecimal amount, String currency, PaymentMethod method);

    boolean verifyCheckoutSignature(String orderId, String paymentId, String signature);

    boolean verifyWebhookSignature(String rawBody, String signature);

    CaptureResult capturePayment(String paymentId, BigDecimal amount, String currency);

    RefundResult initiateRefund(String paymentId, BigDecimal amount, String currency, String reason);

    PaymentVerificationResult fetchPaymentStatus(String paymentId);
}
