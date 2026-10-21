package com.lexor.payment.service;

import com.lexor.payment.dto.request.AdminRefundRequest;
import com.lexor.payment.dto.request.CashCollectionRequest;
import com.lexor.payment.dto.request.InitiatePaymentRequest;
import com.lexor.payment.dto.request.RefundPaymentRequest;
import com.lexor.payment.dto.request.RetryPaymentRequest;
import com.lexor.payment.dto.response.PageResponse;
import com.lexor.payment.dto.response.PaymentMethodDto;
import com.lexor.payment.dto.response.PaymentReceiptResponse;
import com.lexor.payment.dto.response.PaymentResponse;
import com.lexor.payment.dto.response.ReconciliationResponse;
import com.lexor.payment.dto.response.RefundResponse;
import com.lexor.payment.enums.PaymentMethod;
import com.lexor.payment.enums.PaymentStatus;
import com.lexor.payment.security.UserPrincipal;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface IPaymentService {

    List<PaymentMethodDto> getEnabledPaymentMethods();

    PaymentResponse initiatePayment(UserPrincipal principal, InitiatePaymentRequest request, String idempotencyKey, String authHeader);

    PageResponse<PaymentResponse> getRiderPayments(UserPrincipal principal, Pageable pageable);

    PaymentResponse getRiderPaymentByUuid(UserPrincipal principal, String paymentUuid);

    PaymentReceiptResponse getPaymentReceipt(UserPrincipal principal, String paymentUuid, String authHeader);

    PaymentResponse retryPayment(UserPrincipal principal, String paymentUuid, RetryPaymentRequest request);

    RefundResponse requestRefund(UserPrincipal principal, String paymentUuid, RefundPaymentRequest request);

    PaymentResponse getPaymentStatus(UserPrincipal principal, String paymentUuid, boolean refresh);

    PaymentResponse confirmCashCollection(UserPrincipal driverPrincipal, String paymentUuid, CashCollectionRequest request);

    void processWebhook(String provider, String rawBody, String signature);

    PageResponse<PaymentResponse> getAdminPayments(PaymentStatus status, PaymentMethod method, String riderUuid, String driverUuid, Pageable pageable);

    PaymentResponse getAdminPaymentByUuid(String paymentUuid);

    RefundResponse processAdminRefund(UserPrincipal adminPrincipal, String paymentUuid, AdminRefundRequest request);

    ReconciliationResponse getReconciliationReport();
}
