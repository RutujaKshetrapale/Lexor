package com.lexor.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexor.payment.client.IBookingClient;
import com.lexor.payment.client.dto.BookingDetailDto;
import com.lexor.payment.dto.request.AdminRefundRequest;
import com.lexor.payment.dto.request.CashCollectionRequest;
import com.lexor.payment.dto.request.InitiatePaymentRequest;
import com.lexor.payment.dto.request.RefundPaymentRequest;
import com.lexor.payment.entity.PaymentEntity;
import com.lexor.payment.enums.PaymentMethod;
import com.lexor.payment.enums.PaymentStatus;
import com.lexor.payment.repository.FinancialLedgerRepository;
import com.lexor.payment.repository.PaymentRefundRepository;
import com.lexor.payment.repository.PaymentRepository;
import com.lexor.payment.repository.PaymentTransactionRepository;
import com.lexor.payment.repository.PaymentWebhookEventRepository;
import com.lexor.payment.security.JwtTokenProvider;
import com.lexor.payment.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PaymentServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentTransactionRepository transactionRepository;

    @Autowired
    private PaymentRefundRepository refundRepository;

    @Autowired
    private PaymentWebhookEventRepository webhookEventRepository;

    @Autowired
    private FinancialLedgerRepository ledgerRepository;

    @MockBean
    private IBookingClient bookingClient;

    private String riderUuid;
    private String riderToken;
    private String driverUuid;
    private String driverToken;
    private String adminUuid;
    private String adminToken;

    private String bookingUuid;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        refundRepository.deleteAll();
        paymentRepository.deleteAll();
        ledgerRepository.deleteAll();
        webhookEventRepository.deleteAll();

        riderUuid = "22222222-2222-4222-8222-222222222222";
        UserPrincipal riderPrincipal = UserPrincipal.fromClaims(riderUuid, 101L, "rider@lexor.com", "ROLE_RIDER");
        riderToken = "Bearer " + jwtTokenProvider.generateTokenFromUserPrincipal(riderPrincipal);

        driverUuid = "33333333-3333-4333-8333-333333333333";
        UserPrincipal driverPrincipal = UserPrincipal.fromClaims(driverUuid, 201L, "driver@lexor.com", "ROLE_DRIVER");
        driverToken = "Bearer " + jwtTokenProvider.generateTokenFromUserPrincipal(driverPrincipal);

        adminUuid = "11111111-1111-4111-8111-111111111111";
        UserPrincipal adminPrincipal = UserPrincipal.fromClaims(adminUuid, 1L, "admin@lexor.com", "ROLE_ADMIN");
        adminToken = "Bearer " + jwtTokenProvider.generateTokenFromUserPrincipal(adminPrincipal);

        bookingUuid = "55555555-5555-4555-8555-555555555555";

        BookingDetailDto mockBooking = BookingDetailDto.builder()
                .bookingUuid(bookingUuid)
                .status("CONFIRMED")
                .riderUuid(riderUuid)
                .riderName("Rajesh Kumar")
                .driverUuid(driverUuid)
                .driverName("Vikram Singh")
                .vehicleModel("Toyota Camry")
                .licensePlate("KA01AB1234")
                .totalFare(BigDecimal.valueOf(250.00))
                .currency("INR")
                .pickupAddress("Indiranagar, Bengaluru")
                .dropAddress("Prestige Tech Park, Bengaluru")
                .baseFare(BigDecimal.valueOf(50.00))
                .distanceFare(BigDecimal.valueOf(150.00))
                .timeFare(BigDecimal.valueOf(30.00))
                .taxAmount(BigDecimal.valueOf(20.00))
                .build();

        when(bookingClient.getBookingDetails(eq(bookingUuid), any())).thenReturn(Optional.of(mockBooking));
    }

    @Test
    @DisplayName("Health endpoint returns 200 UP")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.service", is("payment-service")));
    }

    @Test
    @DisplayName("List enabled payment methods")
    void testGetPaymentMethods() throws Exception {
        mockMvc.perform(get("/api/v1/payments/methods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].method", is("UPI")))
                .andExpect(jsonPath("$[3].method", is("CASH")));
    }

    @Test
    @DisplayName("Initiate digital UPI payment successfully")
    void testInitiateUpiPaymentSuccess() throws Exception {
        InitiatePaymentRequest req = InitiatePaymentRequest.builder()
                .bookingUuid(bookingUuid)
                .paymentMethod(PaymentMethod.UPI)
                .build();

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentUuid", notNullValue()))
                .andExpect(jsonPath("$.bookingUuid", is(bookingUuid)))
                .andExpect(jsonPath("$.amount", is(250.00)))
                .andExpect(jsonPath("$.paymentMethod", is("UPI")))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.providerOrderId", notNullValue()))
                .andExpect(jsonPath("$.checkoutPayload.upi.intentUrl", notNullValue()));
    }

    @Test
    @DisplayName("Initiate CASH payment without external provider order")
    void testInitiateCashPaymentSuccess() throws Exception {
        InitiatePaymentRequest req = InitiatePaymentRequest.builder()
                .bookingUuid(bookingUuid)
                .paymentMethod(PaymentMethod.CASH)
                .build();

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentUuid", notNullValue()))
                .andExpect(jsonPath("$.paymentMethod", is("CASH")))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.providerOrderId").doesNotExist());
    }

    @Test
    @DisplayName("Idempotency-Key returns cached response on duplicate request")
    void testIdempotencyKeyReturnsCachedResponse() throws Exception {
        String idemKey = "IDEM-" + UUID.randomUUID();
        InitiatePaymentRequest req = InitiatePaymentRequest.builder()
                .bookingUuid(bookingUuid)
                .paymentMethod(PaymentMethod.UPI)
                .build();

        // 1st request
        String res1 = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", riderToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // 2nd request with same idempotency key
        String res2 = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", riderToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertEquals(res1, res2);
        assertEquals(1, paymentRepository.count());
    }

    @Test
    @DisplayName("Rider cannot initiate payment for another rider's booking")
    void testRiderCannotPayForOtherRiderBooking() throws Exception {
        UserPrincipal otherPrincipal = UserPrincipal.fromClaims("other-rider-uuid", 999L, "other@lexor.com", "ROLE_RIDER");
        String otherToken = "Bearer " + jwtTokenProvider.generateTokenFromUserPrincipal(otherPrincipal);

        InitiatePaymentRequest req = InitiatePaymentRequest.builder()
                .bookingUuid(bookingUuid)
                .paymentMethod(PaymentMethod.UPI)
                .build();

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Receipt allowed only for SUCCEEDED payment")
    void testReceiptGenerationAllowedOnlyForSucceeded() throws Exception {
        PaymentEntity payment = PaymentEntity.builder()
                .paymentUuid(UUID.randomUUID().toString())
                .bookingUuid(bookingUuid)
                .riderUuid(riderUuid)
                .driverUuid(driverUuid)
                .amount(BigDecimal.valueOf(250.00))
                .amountPaise(25000L)
                .paymentMethod(PaymentMethod.UPI)
                .status(PaymentStatus.PENDING)
                .build();
        payment = paymentRepository.save(payment);

        // Should fail with 400 Bad Request because status is PENDING
        mockMvc.perform(get("/api/v1/payments/me/" + payment.getPaymentUuid() + "/receipt")
                        .header("Authorization", riderToken))
                .andExpect(status().isBadRequest());

        // Update status to SUCCEEDED
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment = paymentRepository.save(payment);

        // Now should succeed with 200 OK
        mockMvc.perform(get("/api/v1/payments/me/" + payment.getPaymentUuid() + "/receipt")
                        .header("Authorization", riderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptNumber", notNullValue()))
                .andExpect(jsonPath("$.riderName", is("Rajesh Kumar")))
                .andExpect(jsonPath("$.amountPaid", is(250.00)))
                .andExpect(jsonPath("$.fareBreakdown.platformCommission", is(50.00)))
                .andExpect(jsonPath("$.fareBreakdown.driverEarnings", is(200.00)));
    }

    @Test
    @DisplayName("Driver confirms physical cash collection and creates ledger entries")
    void testDriverCashCollectionSuccess() throws Exception {
        PaymentEntity payment = PaymentEntity.builder()
                .paymentUuid(UUID.randomUUID().toString())
                .bookingUuid(bookingUuid)
                .riderUuid(riderUuid)
                .driverUuid(driverUuid)
                .amount(BigDecimal.valueOf(250.00))
                .amountPaise(25000L)
                .paymentMethod(PaymentMethod.CASH)
                .status(PaymentStatus.PENDING)
                .build();
        paymentRepository.save(payment);

        CashCollectionRequest req = CashCollectionRequest.builder()
                .amountCollected(BigDecimal.valueOf(250.00))
                .notes("Exact fare collected in cash")
                .build();

        mockMvc.perform(post("/api/v1/driver/payments/" + payment.getPaymentUuid() + "/cash-collection")
                        .header("Authorization", driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCEEDED")));

        PaymentEntity updated = paymentRepository.findByPaymentUuid(payment.getPaymentUuid()).orElseThrow();
        assertEquals(PaymentStatus.SUCCEEDED, updated.getStatus());
        assertNotNull(updated.getCapturedAt());

        // Verify ledger entries
        assertTrue(ledgerRepository.count() >= 3);
    }

    @Test
    @DisplayName("Unassigned driver cannot collect cash")
    void testUnassignedDriverCannotCollectCash() throws Exception {
        PaymentEntity payment = PaymentEntity.builder()
                .paymentUuid(UUID.randomUUID().toString())
                .bookingUuid(bookingUuid)
                .riderUuid(riderUuid)
                .driverUuid(driverUuid) // assigned to driverUuid
                .amount(BigDecimal.valueOf(250.00))
                .amountPaise(25000L)
                .paymentMethod(PaymentMethod.CASH)
                .status(PaymentStatus.PENDING)
                .build();
        paymentRepository.save(payment);

        UserPrincipal unassignedPrincipal = UserPrincipal.fromClaims("other-driver-uuid", 777L, "otherdriver@lexor.com", "ROLE_DRIVER");
        String unassignedToken = "Bearer " + jwtTokenProvider.generateTokenFromUserPrincipal(unassignedPrincipal);

        mockMvc.perform(post("/api/v1/driver/payments/" + payment.getPaymentUuid() + "/cash-collection")
                        .header("Authorization", unassignedToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Webhook with valid signature captures payment")
    void testWebhookSignatureVerificationAndCapture() throws Exception {
        PaymentEntity payment = PaymentEntity.builder()
                .paymentUuid(UUID.randomUUID().toString())
                .bookingUuid(bookingUuid)
                .riderUuid(riderUuid)
                .driverUuid(driverUuid)
                .amount(BigDecimal.valueOf(250.00))
                .amountPaise(25000L)
                .paymentMethod(PaymentMethod.UPI)
                .providerOrderId("order_test_98765")
                .status(PaymentStatus.PENDING)
                .build();
        paymentRepository.save(payment);

        String rawPayload = "{\"id\":\"evt_12345\",\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_12345\",\"order_id\":\"order_test_98765\",\"amount\":25000,\"currency\":\"INR\",\"status\":\"captured\"}}}}";
        String validSignature = calculateHmacSha256(rawPayload, "sample_webhook_secret_987654");

        mockMvc.perform(post("/api/v1/payments/webhooks/razorpay")
                        .header("X-Razorpay-Signature", validSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawPayload))
                .andExpect(status().isOk());

        PaymentEntity updated = paymentRepository.findByPaymentUuid(payment.getPaymentUuid()).orElseThrow();
        assertEquals(PaymentStatus.SUCCEEDED, updated.getStatus());
        assertEquals("pay_12345", updated.getProviderPaymentId());
    }

    @Test
    @DisplayName("Webhook with invalid signature is rejected with 400 Bad Request")
    void testWebhookInvalidSignatureRejected() throws Exception {
        String rawPayload = "{\"id\":\"evt_bad\",\"event\":\"payment.captured\"}";

        mockMvc.perform(post("/api/v1/payments/webhooks/razorpay")
                        .header("X-Razorpay-Signature", "invalid_signature_hex")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Rider refund flow updates status and creates ledger entry")
    void testRiderRefundFlow() throws Exception {
        PaymentEntity payment = PaymentEntity.builder()
                .paymentUuid(UUID.randomUUID().toString())
                .bookingUuid(bookingUuid)
                .riderUuid(riderUuid)
                .driverUuid(driverUuid)
                .amount(BigDecimal.valueOf(250.00))
                .amountPaise(25000L)
                .paymentMethod(PaymentMethod.UPI)
                .providerPaymentId("pay_test_refund_99")
                .status(PaymentStatus.SUCCEEDED)
                .build();
        paymentRepository.save(payment);

        RefundPaymentRequest req = RefundPaymentRequest.builder()
                .amount(BigDecimal.valueOf(250.00))
                .reason("Driver did not arrive")
                .build();

        mockMvc.perform(post("/api/v1/payments/" + payment.getPaymentUuid() + "/refund")
                        .header("Authorization", riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PROCESSED")))
                .andExpect(jsonPath("$.amount", is(250.00)));

        PaymentEntity updated = paymentRepository.findByPaymentUuid(payment.getPaymentUuid()).orElseThrow();
        assertEquals(PaymentStatus.REFUNDED, updated.getStatus());
    }

    @Test
    @DisplayName("Admin refund and reconciliation endpoints")
    void testAdminRefundAndReconciliation() throws Exception {
        PaymentEntity payment = PaymentEntity.builder()
                .paymentUuid(UUID.randomUUID().toString())
                .bookingUuid(bookingUuid)
                .riderUuid(riderUuid)
                .driverUuid(driverUuid)
                .amount(BigDecimal.valueOf(250.00))
                .amountPaise(25000L)
                .paymentMethod(PaymentMethod.UPI)
                .providerPaymentId("pay_admin_ref_1")
                .status(PaymentStatus.SUCCEEDED)
                .build();
        paymentRepository.save(payment);

        AdminRefundRequest req = AdminRefundRequest.builder()
                .amount(BigDecimal.valueOf(100.00))
                .reason("Courtesy refund for delayed trip")
                .adminNotes("Approved by supervisor")
                .build();

        mockMvc.perform(post("/api/v1/admin/payments/" + payment.getPaymentUuid() + "/refund")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PROCESSED")))
                .andExpect(jsonPath("$.amount", is(100.00)));

        mockMvc.perform(get("/api/v1/admin/payments/reconciliation")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("HEALTHY")))
                .andExpect(jsonPath("$.totalPaymentsCount", greaterThanOrEqualTo(1)));
    }

    private String calculateHmacSha256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hmacBytes);
    }
}
