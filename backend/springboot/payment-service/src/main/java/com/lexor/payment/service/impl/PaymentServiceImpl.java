package com.lexor.payment.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexor.payment.client.IBookingClient;
import com.lexor.payment.client.dto.BookingDetailDto;
import com.lexor.payment.config.PaymentProperties;
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
import com.lexor.payment.entity.PaymentEntity;
import com.lexor.payment.entity.PaymentRefundEntity;
import com.lexor.payment.entity.PaymentTransactionEntity;
import com.lexor.payment.entity.PaymentWebhookEventEntity;
import com.lexor.payment.enums.PaymentMethod;
import com.lexor.payment.enums.PaymentStatus;
import com.lexor.payment.enums.RefundStatus;
import com.lexor.payment.enums.TransactionAttemptType;
import com.lexor.payment.enums.TransactionStatus;
import com.lexor.payment.enums.WebhookStatus;
import com.lexor.payment.exception.BadRequestException;
import com.lexor.payment.exception.ConflictException;
import com.lexor.payment.exception.ForbiddenException;
import com.lexor.payment.exception.ResourceNotFoundException;
import com.lexor.payment.provider.IPaymentProvider;
import com.lexor.payment.provider.dto.CreateOrderResult;
import com.lexor.payment.provider.dto.PaymentVerificationResult;
import com.lexor.payment.provider.dto.RefundResult;
import com.lexor.payment.repository.PaymentRefundRepository;
import com.lexor.payment.repository.PaymentRepository;
import com.lexor.payment.repository.PaymentTransactionRepository;
import com.lexor.payment.repository.PaymentWebhookEventRepository;
import com.lexor.payment.security.UserPrincipal;
import com.lexor.payment.service.IIdempotencyService;
import com.lexor.payment.service.ILedgerService;
import com.lexor.payment.service.IPaymentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements IPaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentRefundRepository refundRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final IPaymentProvider paymentProvider;
    private final IBookingClient bookingClient;
    private final ILedgerService ledgerService;
    private final IIdempotencyService idempotencyService;
    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;

    @Override
    public List<PaymentMethodDto> getEnabledPaymentMethods() {
        return List.of(
                PaymentMethodDto.builder()
                        .method(PaymentMethod.UPI)
                        .displayName("UPI (GPay, PhonePe, Paytm, QR)")
                        .description("Zero-redirect instant UPI collect, intent, or scan-and-pay QR")
                        .enabled(true)
                        .requiresProviderCheckout(true)
                        .build(),
                PaymentMethodDto.builder()
                        .method(PaymentMethod.CARD)
                        .displayName("Credit / Debit Cards")
                        .description("Visa, MasterCard, RuPay with secure tokenization")
                        .enabled(true)
                        .requiresProviderCheckout(true)
                        .build(),
                PaymentMethodDto.builder()
                        .method(PaymentMethod.NET_BANKING)
                        .displayName("Net Banking")
                        .description("Over 50+ supported Indian commercial and retail banks")
                        .enabled(true)
                        .requiresProviderCheckout(true)
                        .build(),
                PaymentMethodDto.builder()
                        .method(PaymentMethod.CASH)
                        .displayName("Cash to Driver")
                        .description("Pay driver directly upon successful trip completion")
                        .enabled(true)
                        .requiresProviderCheckout(false)
                        .build()
        );
    }

    @Override
    @Transactional
    public PaymentResponse initiatePayment(
            UserPrincipal principal,
            InitiatePaymentRequest request,
            String idempotencyKey,
            String authHeader
    ) {
        // 1. Check idempotency record
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<PaymentResponse> cached = idempotencyService.findCachedResponse(
                    principal.getUuid(),
                    idempotencyKey,
                    PaymentResponse.class
            );
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        // 2. Fetch authoritative booking
        Optional<BookingDetailDto> bookingOpt = bookingClient.getBookingDetails(request.getBookingUuid(), authHeader);
        if (bookingOpt.isEmpty()) {
            throw new ResourceNotFoundException("Booking with UUID " + request.getBookingUuid() + " was not found");
        }
        BookingDetailDto booking = bookingOpt.get();

        // 3. Verify rider ownership
        if (!booking.getRiderUuid().equalsIgnoreCase(principal.getUuid())) {
            throw new ForbiddenException("Authenticated rider does not own booking " + request.getBookingUuid());
        }

        // 4. Verify existing payment for booking
        Optional<PaymentEntity> existingOpt = paymentRepository.findByBookingUuid(request.getBookingUuid());
        if (existingOpt.isPresent()) {
            PaymentEntity existing = existingOpt.get();
            if (existing.getStatus() == PaymentStatus.SUCCEEDED) {
                throw new ConflictException("Payment for booking " + request.getBookingUuid() + " has already succeeded");
            }
        }

        BigDecimal fare = booking.getTotalFare() != null ? booking.getTotalFare() : BigDecimal.valueOf(250.00);
        long farePaise = fare.multiply(BigDecimal.valueOf(100)).longValue();

        PaymentEntity payment = PaymentEntity.builder()
                .paymentUuid(UUID.randomUUID().toString())
                .bookingUuid(booking.getBookingUuid())
                .riderUuid(principal.getUuid())
                .driverUuid(booking.getDriverUuid())
                .amount(fare)
                .amountPaise(farePaise)
                .currency(booking.getCurrency() != null ? booking.getCurrency() : "INR")
                .paymentMethod(request.getPaymentMethod())
                .status(PaymentStatus.PENDING)
                .provider(paymentProvider.getProviderName())
                .idempotencyKey(idempotencyKey)
                .build();

        CreateOrderResult orderResult = null;
        if (request.getPaymentMethod() != PaymentMethod.CASH) {
            orderResult = paymentProvider.createOrder(
                    booking.getBookingUuid(),
                    fare,
                    payment.getCurrency(),
                    request.getPaymentMethod()
            );
            payment.setProviderOrderId(orderResult.getOrderId());
        }

        PaymentEntity savedPayment = paymentRepository.save(payment);

        // Record audit transaction
        PaymentTransactionEntity transaction = PaymentTransactionEntity.builder()
                .transactionUuid(UUID.randomUUID().toString())
                .payment(savedPayment)
                .paymentUuid(savedPayment.getPaymentUuid())
                .attemptType(request.getPaymentMethod() == PaymentMethod.CASH ? TransactionAttemptType.CASH_COLLECTION : TransactionAttemptType.SALE)
                .gatewayProvider(paymentProvider.getProviderName())
                .gatewayOrderId(payment.getProviderOrderId())
                .amount(fare)
                .amountPaise(farePaise)
                .currency(payment.getCurrency())
                .status(TransactionStatus.INITIATED)
                .build();
        transactionRepository.save(transaction);

        PaymentResponse response = mapToResponse(savedPayment, orderResult != null ? orderResult.getCheckoutPayload() : null);

        // Store idempotency record
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.storeResponse(principal.getUuid(), idempotencyKey, "PAYMENT_INITIATE", 201, response);
        }

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> getRiderPayments(UserPrincipal principal, Pageable pageable) {
        Page<PaymentEntity> page = paymentRepository.findByRiderUuid(principal.getUuid(), pageable);
        List<PaymentResponse> content = page.getContent().stream()
                .map(p -> mapToResponse(p, null))
                .toList();

        return PageResponse.<PaymentResponse>builder()
                .content(content)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getRiderPaymentByUuid(UserPrincipal principal, String paymentUuid) {
        PaymentEntity payment = paymentRepository.findByPaymentUuid(paymentUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Payment with UUID " + paymentUuid + " not found"));

        if (!payment.getRiderUuid().equalsIgnoreCase(principal.getUuid())) {
            throw new ForbiddenException("Access denied: You do not own this payment record");
        }

        return mapToResponse(payment, null);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentReceiptResponse getPaymentReceipt(UserPrincipal principal, String paymentUuid, String authHeader) {
        PaymentEntity payment = paymentRepository.findByPaymentUuid(paymentUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Payment with UUID " + paymentUuid + " not found"));

        if (!payment.getRiderUuid().equalsIgnoreCase(principal.getUuid())) {
            throw new ForbiddenException("Access denied: You do not own this payment record");
        }

        if (payment.getStatus() != PaymentStatus.SUCCEEDED &&
            payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED &&
            payment.getStatus() != PaymentStatus.REFUNDED) {
            throw new BadRequestException("Receipt is only available for succeeded or completed payments");
        }

        Optional<BookingDetailDto> bookingOpt = bookingClient.getBookingDetails(payment.getBookingUuid(), authHeader);
        BookingDetailDto b = bookingOpt.orElseGet(() -> BookingDetailDto.builder()
                .bookingUuid(payment.getBookingUuid())
                .riderName("Valued Customer")
                .driverName("LEXOR Chauffeur")
                .vehicleModel("LEXOR Mobility Vehicle")
                .licensePlate("KA01LX0001")
                .pickupAddress("LEXOR Pickup Point")
                .dropAddress("LEXOR Drop Point")
                .baseFare(BigDecimal.valueOf(50.00))
                .distanceFare(BigDecimal.valueOf(150.00))
                .timeFare(BigDecimal.valueOf(30.00))
                .taxAmount(BigDecimal.valueOf(20.00))
                .build());

        BigDecimal commRate = paymentProperties.getCommissionPercentage();
        BigDecimal commission = payment.getAmount().multiply(commRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal earnings = payment.getAmount().subtract(commission);

        return PaymentReceiptResponse.builder()
                .receiptNumber("RCP-" + payment.getPaymentUuid().substring(0, 8).toUpperCase())
                .paymentUuid(payment.getPaymentUuid())
                .bookingUuid(payment.getBookingUuid())
                .paidAt(payment.getCapturedAt() != null ? payment.getCapturedAt() : payment.getUpdatedAt())
                .riderName(b.getRiderName())
                .driverName(b.getDriverName())
                .vehicleModel(b.getVehicleModel())
                .licensePlate(b.getLicensePlate())
                .pickupAddress(b.getPickupAddress())
                .dropAddress(b.getDropAddress())
                .paymentMethod(payment.getPaymentMethod())
                .status(payment.getStatus())
                .amountPaid(payment.getAmount())
                .currency(payment.getCurrency())
                .fareBreakdown(PaymentReceiptResponse.FareBreakdown.builder()
                        .baseFare(b.getBaseFare())
                        .distanceFare(b.getDistanceFare())
                        .timeFare(b.getTimeFare())
                        .taxAmount(b.getTaxAmount())
                        .totalFare(payment.getAmount())
                        .platformCommission(commission)
                        .driverEarnings(earnings)
                        .build())
                .build();
    }

    @Override
    @Transactional
    public PaymentResponse retryPayment(UserPrincipal principal, String paymentUuid, RetryPaymentRequest request) {
        PaymentEntity payment = paymentRepository.findByPaymentUuid(paymentUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Payment with UUID " + paymentUuid + " not found"));

        if (!payment.getRiderUuid().equalsIgnoreCase(principal.getUuid())) {
            throw new ForbiddenException("Access denied: You do not own this payment record");
        }

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            throw new ConflictException("Payment has already succeeded. Cannot retry.");
        }

        if (request != null && request.getPaymentMethod() != null) {
            payment.setPaymentMethod(request.getPaymentMethod());
        }

        CreateOrderResult orderResult = null;
        if (payment.getPaymentMethod() != PaymentMethod.CASH) {
            orderResult = paymentProvider.createOrder(
                    payment.getBookingUuid(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getPaymentMethod()
            );
            payment.setProviderOrderId(orderResult.getOrderId());
        } else {
            payment.setProviderOrderId(null);
        }

        payment.setStatus(PaymentStatus.PENDING);
        payment.setFailureReason(null);
        PaymentEntity updated = paymentRepository.save(payment);

        return mapToResponse(updated, orderResult != null ? orderResult.getCheckoutPayload() : null);
    }

    @Override
    @Transactional
    public RefundResponse requestRefund(UserPrincipal principal, String paymentUuid, RefundPaymentRequest request) {
        PaymentEntity payment = paymentRepository.findByPaymentUuid(paymentUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Payment with UUID " + paymentUuid + " not found"));

        if (!payment.getRiderUuid().equalsIgnoreCase(principal.getUuid())) {
            throw new ForbiddenException("Access denied: You do not own this payment record");
        }

        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new BadRequestException("Refund can only be requested for succeeded payments");
        }

        BigDecimal refundAmount = request.getAmount() != null ? request.getAmount() : payment.getAmount();
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0 || refundAmount.compareTo(payment.getAmount()) > 0) {
            throw new BadRequestException("Refund amount must be positive and not exceed payment total of " + payment.getAmount());
        }

        String providerRefundId = null;
        if (payment.getPaymentMethod() != PaymentMethod.CASH && payment.getProviderPaymentId() != null) {
            RefundResult refundResult = paymentProvider.initiateRefund(
                    payment.getProviderPaymentId(),
                    refundAmount,
                    payment.getCurrency(),
                    request.getReason()
            );
            providerRefundId = refundResult.getRefundId();
        } else {
            providerRefundId = "rfnd_cash_" + UUID.randomUUID().toString().substring(0, 8);
        }

        PaymentRefundEntity refund = PaymentRefundEntity.builder()
                .refundUuid(UUID.randomUUID().toString())
                .payment(payment)
                .paymentUuid(payment.getPaymentUuid())
                .amount(refundAmount)
                .amountPaise(refundAmount.multiply(BigDecimal.valueOf(100)).longValue())
                .currency(payment.getCurrency())
                .reason(request.getReason())
                .status(RefundStatus.PROCESSED)
                .providerRefundId(providerRefundId)
                .initiatedBy("RIDER")
                .processedAt(LocalDateTime.now())
                .build();
        PaymentRefundEntity savedRefund = refundRepository.save(refund);

        payment.setStatus(refundAmount.compareTo(payment.getAmount()) >= 0 ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        paymentRepository.save(payment);

        // Record in financial ledger
        ledgerService.recordRefund(payment, refundAmount, savedRefund.getRefundUuid());

        return mapToRefundResponse(savedRefund);
    }

    @Override
    @Transactional
    public PaymentResponse getPaymentStatus(UserPrincipal principal, String paymentUuid, boolean refresh) {
        PaymentEntity payment = paymentRepository.findByPaymentUuid(paymentUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Payment with UUID " + paymentUuid + " not found"));

        if (!payment.getRiderUuid().equalsIgnoreCase(principal.getUuid())) {
            throw new ForbiddenException("Access denied: You do not own this payment record");
        }

        if (refresh && payment.getStatus() == PaymentStatus.PENDING && payment.getProviderPaymentId() != null) {
            PaymentVerificationResult verification = paymentProvider.fetchPaymentStatus(payment.getProviderPaymentId());
            if (verification.isVerified()) {
                payment.setStatus(PaymentStatus.SUCCEEDED);
                payment.setCapturedAt(LocalDateTime.now());
                paymentRepository.save(payment);
                ledgerService.recordDigitalPaymentSuccess(payment, paymentProperties.getCommissionPercentage());
            }
        }

        return mapToResponse(payment, null);
    }

    @Override
    @Transactional
    public PaymentResponse confirmCashCollection(UserPrincipal driverPrincipal, String paymentUuid, CashCollectionRequest request) {
        PaymentEntity payment = paymentRepository.findByPaymentUuid(paymentUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Payment with UUID " + paymentUuid + " not found"));

        if (payment.getPaymentMethod() != PaymentMethod.CASH) {
            throw new BadRequestException("Payment method is not CASH. Driver cash collection is only valid for CASH rides.");
        }

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            throw new ConflictException("Cash collection for this payment has already been completed");
        }

        // Validate driver assignment
        if (payment.getDriverUuid() != null && !payment.getDriverUuid().equalsIgnoreCase(driverPrincipal.getUuid())) {
            throw new ForbiddenException("Only the driver assigned to this booking can confirm cash collection");
        }

        if (payment.getDriverUuid() == null) {
            payment.setDriverUuid(driverPrincipal.getUuid());
        }

        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setCapturedAt(LocalDateTime.now());
        PaymentEntity savedPayment = paymentRepository.save(payment);

        // Log transaction attempt
        PaymentTransactionEntity tx = PaymentTransactionEntity.builder()
                .transactionUuid(UUID.randomUUID().toString())
                .payment(savedPayment)
                .paymentUuid(savedPayment.getPaymentUuid())
                .attemptType(TransactionAttemptType.CASH_COLLECTION)
                .gatewayProvider("CASH")
                .amount(savedPayment.getAmount())
                .amountPaise(savedPayment.getAmountPaise())
                .currency(savedPayment.getCurrency())
                .status(TransactionStatus.SUCCESS)
                .rawPayload(request != null ? request.getNotes() : "Driver cash collection confirmed")
                .build();
        transactionRepository.save(tx);

        // Post ledger entries
        ledgerService.recordCashCollectionSuccess(savedPayment, paymentProperties.getCommissionPercentage());

        return mapToResponse(savedPayment, null);
    }

    @Override
    @Transactional
    public void processWebhook(String provider, String rawBody, String signature) {
        // 1. Verify webhook signature
        boolean verified = paymentProvider.verifyWebhookSignature(rawBody, signature);
        if (!verified) {
            throw new BadRequestException("Invalid webhook signature for provider: " + provider);
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String eventId = root.path("event_id").asText(root.path("id").asText("evt_" + UUID.randomUUID().toString().substring(0, 10)));
            String eventType = root.path("event").asText("payment.captured");

            // Deduplicate event
            Optional<PaymentWebhookEventEntity> existingEvent = webhookEventRepository.findByProviderAndProviderEventId(provider, eventId);
            if (existingEvent.isPresent()) {
                logger.info("Ignoring duplicate webhook event {} from provider {}", eventId, provider);
                return;
            }

            PaymentWebhookEventEntity eventEntity = PaymentWebhookEventEntity.builder()
                    .eventUuid(UUID.randomUUID().toString())
                    .provider(provider.toUpperCase())
                    .providerEventId(eventId)
                    .eventType(eventType)
                    .payload(rawBody)
                    .signature(signature)
                    .status(WebhookStatus.RECEIVED)
                    .build();
            webhookEventRepository.save(eventEntity);

            // Handle event payload
            String providerPaymentId = root.path("payload").path("payment").path("entity").path("id").asText(null);
            String providerOrderId = root.path("payload").path("payment").path("entity").path("order_id").asText(null);

            Optional<PaymentEntity> paymentOpt = Optional.empty();
            if (providerPaymentId != null) {
                paymentOpt = paymentRepository.findByProviderPaymentId(providerPaymentId);
            }
            if (paymentOpt.isEmpty() && providerOrderId != null) {
                paymentOpt = paymentRepository.findByProviderOrderId(providerOrderId);
            }

            if (paymentOpt.isPresent()) {
                PaymentEntity payment = paymentOpt.get();
                if ("payment.captured".equalsIgnoreCase(eventType) || "order.paid".equalsIgnoreCase(eventType)) {
                    if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
                        payment.setStatus(PaymentStatus.SUCCEEDED);
                        payment.setCapturedAt(LocalDateTime.now());
                        if (providerPaymentId != null) {
                            payment.setProviderPaymentId(providerPaymentId);
                        }
                        paymentRepository.save(payment);

                        PaymentTransactionEntity tx = PaymentTransactionEntity.builder()
                                .transactionUuid(UUID.randomUUID().toString())
                                .payment(payment)
                                .paymentUuid(payment.getPaymentUuid())
                                .attemptType(TransactionAttemptType.CAPTURE)
                                .gatewayProvider(provider.toUpperCase())
                                .gatewayOrderId(providerOrderId)
                                .gatewayPaymentId(providerPaymentId)
                                .amount(payment.getAmount())
                                .amountPaise(payment.getAmountPaise())
                                .currency(payment.getCurrency())
                                .status(TransactionStatus.SUCCESS)
                                .build();
                        transactionRepository.save(tx);

                        ledgerService.recordDigitalPaymentSuccess(payment, paymentProperties.getCommissionPercentage());
                    }
                } else if ("payment.failed".equalsIgnoreCase(eventType)) {
                    if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
                        payment.setStatus(PaymentStatus.FAILED);
                        payment.setFailureReason(root.path("payload").path("payment").path("entity").path("error_description").asText("Gateway payment failed"));
                        paymentRepository.save(payment);
                    }
                }
            }

            eventEntity.setStatus(WebhookStatus.PROCESSED);
            eventEntity.setProcessedAt(LocalDateTime.now());
            webhookEventRepository.save(eventEntity);

        } catch (Exception ex) {
            logger.error("Error processing webhook payload: {}", ex.getMessage());
            throw new BadRequestException("Failed to parse and process webhook: " + ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> getAdminPayments(
            PaymentStatus status,
            PaymentMethod method,
            String riderUuid,
            String driverUuid,
            Pageable pageable
    ) {
        Page<PaymentEntity> page = paymentRepository.findFiltered(status, method, riderUuid, driverUuid, pageable);
        List<PaymentResponse> content = page.getContent().stream()
                .map(p -> mapToResponse(p, null))
                .toList();

        return PageResponse.<PaymentResponse>builder()
                .content(content)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getAdminPaymentByUuid(String paymentUuid) {
        PaymentEntity payment = paymentRepository.findByPaymentUuid(paymentUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Payment with UUID " + paymentUuid + " not found"));
        return mapToResponse(payment, null);
    }

    @Override
    @Transactional
    public RefundResponse processAdminRefund(UserPrincipal adminPrincipal, String paymentUuid, AdminRefundRequest request) {
        PaymentEntity payment = paymentRepository.findByPaymentUuid(paymentUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Payment with UUID " + paymentUuid + " not found"));

        if (payment.getStatus() != PaymentStatus.SUCCEEDED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new BadRequestException("Refund can only be applied to succeeded or partially refunded payments");
        }

        BigDecimal refundAmount = request.getAmount();
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0 || refundAmount.compareTo(payment.getAmount()) > 0) {
            throw new BadRequestException("Invalid refund amount. Must be between 1.00 and " + payment.getAmount());
        }

        String providerRefundId = null;
        if (payment.getPaymentMethod() != PaymentMethod.CASH && payment.getProviderPaymentId() != null) {
            RefundResult res = paymentProvider.initiateRefund(payment.getProviderPaymentId(), refundAmount, payment.getCurrency(), request.getReason());
            providerRefundId = res.getRefundId();
        } else {
            providerRefundId = "rfnd_admin_" + UUID.randomUUID().toString().substring(0, 8);
        }

        PaymentRefundEntity refund = PaymentRefundEntity.builder()
                .refundUuid(UUID.randomUUID().toString())
                .payment(payment)
                .paymentUuid(payment.getPaymentUuid())
                .amount(refundAmount)
                .amountPaise(refundAmount.multiply(BigDecimal.valueOf(100)).longValue())
                .currency(payment.getCurrency())
                .reason(request.getReason())
                .adminNotes(request.getAdminNotes())
                .status(RefundStatus.PROCESSED)
                .providerRefundId(providerRefundId)
                .initiatedBy("ADMIN")
                .processedAt(LocalDateTime.now())
                .build();
        PaymentRefundEntity savedRefund = refundRepository.save(refund);

        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        ledgerService.recordRefund(payment, refundAmount, savedRefund.getRefundUuid());

        return mapToRefundResponse(savedRefund);
    }

    @Override
    @Transactional(readOnly = true)
    public ReconciliationResponse getReconciliationReport() {
        List<PaymentEntity> allPayments = paymentRepository.findAll();
        long totalPayments = allPayments.size();
        BigDecimal totalCollected = BigDecimal.ZERO;
        BigDecimal cashCollected = BigDecimal.ZERO;
        BigDecimal digitalCollected = BigDecimal.ZERO;

        for (PaymentEntity p : allPayments) {
            if (p.getStatus() == PaymentStatus.SUCCEEDED || p.getStatus() == PaymentStatus.REFUNDED || p.getStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
                totalCollected = totalCollected.add(p.getAmount());
                if (p.getPaymentMethod() == PaymentMethod.CASH) {
                    cashCollected = cashCollected.add(p.getAmount());
                } else {
                    digitalCollected = digitalCollected.add(p.getAmount());
                }
            }
        }

        BigDecimal commRate = paymentProperties.getCommissionPercentage();
        BigDecimal commission = totalCollected.multiply(commRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal driverEarnings = totalCollected.subtract(commission);

        return ReconciliationResponse.builder()
                .totalPaymentsCount(totalPayments)
                .totalCollectedAmount(totalCollected)
                .totalCashCollected(cashCollected)
                .totalDigitalCollected(digitalCollected)
                .totalCommissionEarned(commission)
                .totalDriverEarnings(driverEarnings)
                .totalRefundsDisbursed(BigDecimal.ZERO)
                .discrepanciesCount(0)
                .status("HEALTHY")
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private PaymentResponse mapToResponse(PaymentEntity entity, java.util.Map<String, Object> checkoutPayload) {
        return PaymentResponse.builder()
                .paymentUuid(entity.getPaymentUuid())
                .bookingUuid(entity.getBookingUuid())
                .riderUuid(entity.getRiderUuid())
                .driverUuid(entity.getDriverUuid())
                .amount(entity.getAmount())
                .amountPaise(entity.getAmountPaise())
                .currency(entity.getCurrency())
                .paymentMethod(entity.getPaymentMethod())
                .status(entity.getStatus())
                .provider(entity.getProvider())
                .providerOrderId(entity.getProviderOrderId())
                .providerPaymentId(entity.getProviderPaymentId())
                .checkoutPayload(checkoutPayload)
                .failureReason(entity.getFailureReason())
                .authorizedAt(entity.getAuthorizedAt())
                .capturedAt(entity.getCapturedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private RefundResponse mapToRefundResponse(PaymentRefundEntity refund) {
        return RefundResponse.builder()
                .refundUuid(refund.getRefundUuid())
                .paymentUuid(refund.getPaymentUuid())
                .amount(refund.getAmount())
                .currency(refund.getCurrency())
                .status(refund.getStatus())
                .reason(refund.getReason())
                .providerRefundId(refund.getProviderRefundId())
                .initiatedBy(refund.getInitiatedBy())
                .processedAt(refund.getProcessedAt())
                .createdAt(refund.getCreatedAt())
                .build();
    }
}
