package com.lexor.payment.controller;

import com.lexor.payment.dto.request.InitiatePaymentRequest;
import com.lexor.payment.dto.request.RefundPaymentRequest;
import com.lexor.payment.dto.request.RetryPaymentRequest;
import com.lexor.payment.dto.response.ErrorResponse;
import com.lexor.payment.dto.response.PageResponse;
import com.lexor.payment.dto.response.PaymentMethodDto;
import com.lexor.payment.dto.response.PaymentReceiptResponse;
import com.lexor.payment.dto.response.PaymentResponse;
import com.lexor.payment.dto.response.RefundResponse;
import com.lexor.payment.security.UserPrincipal;
import com.lexor.payment.service.IPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Rider Payments", description = "Endpoints for riders to initiate payments, query payment history, view receipts, and request refunds")
@SecurityRequirement(name = "BearerAuthentication")
public class RiderPaymentController {

    private final IPaymentService paymentService;

    @Operation(summary = "List supported payment methods", description = "Returns active payment methods configured on LEXOR (UPI, Card, Net Banking, Cash)")
    @GetMapping("/methods")
    public ResponseEntity<List<PaymentMethodDto>> getPaymentMethods() {
        return ResponseEntity.ok(paymentService.getEnabledPaymentMethods());
    }

    @Operation(summary = "Initiate payment for booking", description = "Creates payment intent or order session linked to authoritative booking")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment initiated successfully", content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Rider does not own booking", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Booking not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflict - Payment already succeeded", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<PaymentResponse> initiatePayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody InitiatePaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        PaymentResponse response = paymentService.initiatePayment(principal, request, idempotencyKey, authHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List authenticated rider's payment history", description = "Returns paginated payment transactions owned by caller")
    @GetMapping("/me")
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<PageResponse<PaymentResponse>> getMyPayments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(paymentService.getRiderPayments(principal, pageable));
    }

    @Operation(summary = "Get rider payment details by UUID", description = "Returns single payment details for authenticated owner")
    @GetMapping("/me/{paymentUuid}")
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<PaymentResponse> getMyPaymentByUuid(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String paymentUuid
    ) {
        return ResponseEntity.ok(paymentService.getRiderPaymentByUuid(principal, paymentUuid));
    }

    @Operation(summary = "Get official ride payment receipt", description = "Generates and returns receipt for completed payment")
    @GetMapping("/me/{paymentUuid}/receipt")
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<PaymentReceiptResponse> getPaymentReceipt(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String paymentUuid,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        return ResponseEntity.ok(paymentService.getPaymentReceipt(principal, paymentUuid, authHeader));
    }

    @Operation(summary = "Retry failed or expired payment", description = "Retries payment initiation with optional updated method")
    @PostMapping("/{paymentUuid}/retry")
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<PaymentResponse> retryPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String paymentUuid,
            @Valid @RequestBody(required = false) RetryPaymentRequest request
    ) {
        return ResponseEntity.ok(paymentService.retryPayment(principal, paymentUuid, request));
    }

    @Operation(summary = "Request refund for ride payment", description = "Requests full or partial refund for succeeded payment")
    @PostMapping("/{paymentUuid}/refund")
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<RefundResponse> requestRefund(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String paymentUuid,
            @Valid @RequestBody RefundPaymentRequest request
    ) {
        return ResponseEntity.ok(paymentService.requestRefund(principal, paymentUuid, request));
    }

    @Operation(summary = "Get authoritative payment status", description = "Queries status and optionally refreshes from payment provider")
    @GetMapping("/{paymentUuid}/status")
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<PaymentResponse> getPaymentStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String paymentUuid,
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh
    ) {
        return ResponseEntity.ok(paymentService.getPaymentStatus(principal, paymentUuid, refresh));
    }
}
