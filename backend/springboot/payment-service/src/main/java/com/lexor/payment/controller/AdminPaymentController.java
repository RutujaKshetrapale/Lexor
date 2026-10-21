package com.lexor.payment.controller;

import com.lexor.payment.dto.request.AdminRefundRequest;
import com.lexor.payment.dto.response.ErrorResponse;
import com.lexor.payment.dto.response.PageResponse;
import com.lexor.payment.dto.response.PaymentResponse;
import com.lexor.payment.dto.response.ReconciliationResponse;
import com.lexor.payment.dto.response.RefundResponse;
import com.lexor.payment.entity.PaymentTransactionEntity;
import com.lexor.payment.enums.PaymentMethod;
import com.lexor.payment.enums.PaymentStatus;
import com.lexor.payment.repository.PaymentTransactionRepository;
import com.lexor.payment.security.UserPrincipal;
import com.lexor.payment.service.IPaymentService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
@Tag(name = "Administrator Payments", description = "Privileged endpoints for auditing platform payments, issuing refunds, and financial reconciliation")
@SecurityRequirement(name = "BearerAuthentication")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final IPaymentService paymentService;
    private final PaymentTransactionRepository transactionRepository;

    @Operation(summary = "Admin: List and filter all payments", description = "Query platform-wide payments with optional status, method, and user filters")
    @GetMapping
    public ResponseEntity<PageResponse<PaymentResponse>> getAllPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) String riderUuid,
            @RequestParam(required = false) String driverUuid,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(paymentService.getAdminPayments(status, method, riderUuid, driverUuid, pageable));
    }

    @Operation(summary = "Admin: Get payment details by UUID", description = "Inspect authoritative details of a specific payment")
    @GetMapping("/{paymentUuid}")
    public ResponseEntity<PaymentResponse> getPaymentByUuid(@PathVariable String paymentUuid) {
        return ResponseEntity.ok(paymentService.getAdminPaymentByUuid(paymentUuid));
    }

    @Operation(summary = "Admin: Issue refund with audit notes", description = "Privileged administrator refund with mandatory reason code and audit notes")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Refund approved and processed", content = @Content(schema = @Schema(implementation = RefundResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Ineligible status or invalid amount", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ROLE_ADMIN", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Payment not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{paymentUuid}/refund")
    public ResponseEntity<RefundResponse> processRefund(
            @AuthenticationPrincipal UserPrincipal adminPrincipal,
            @PathVariable String paymentUuid,
            @Valid @RequestBody AdminRefundRequest request
    ) {
        return ResponseEntity.ok(paymentService.processAdminRefund(adminPrincipal, paymentUuid, request));
    }

    @Operation(summary = "Admin: Platform financial reconciliation", description = "Generates financial reconciliation metrics, ledger balances, and discrepancy audit")
    @GetMapping("/reconciliation")
    public ResponseEntity<ReconciliationResponse> getReconciliation() {
        return ResponseEntity.ok(paymentService.getReconciliationReport());
    }

    @Operation(summary = "Admin: Payment transaction audit history", description = "Returns complete low-level attempt log and gateway payloads for a payment")
    @GetMapping("/{paymentUuid}/audit")
    public ResponseEntity<List<PaymentTransactionEntity>> getPaymentAudit(@PathVariable String paymentUuid) {
        return ResponseEntity.ok(transactionRepository.findByPaymentUuidOrderByCreatedAtDesc(paymentUuid));
    }
}
