package com.lexor.payment.controller;

import com.lexor.payment.dto.request.CashCollectionRequest;
import com.lexor.payment.dto.response.ErrorResponse;
import com.lexor.payment.dto.response.PaymentResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/driver/payments")
@RequiredArgsConstructor
@Tag(name = "Driver Payments", description = "Endpoints for drivers to record and confirm physical cash collection for cash trips")
@SecurityRequirement(name = "BearerAuthentication")
@PreAuthorize("hasRole('DRIVER')")
public class DriverPaymentController {

    private final IPaymentService paymentService;

    @Operation(summary = "Confirm driver cash collection", description = "Authoritative confirmation by the assigned driver that physical cash was collected from rider")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cash collection confirmed successfully", content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - Payment method is not CASH or invalid state", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Driver is not assigned to this booking", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Payment not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflict - Cash already collected", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{paymentUuid}/cash-collection")
    public ResponseEntity<PaymentResponse> confirmCashCollection(
            @AuthenticationPrincipal UserPrincipal driverPrincipal,
            @PathVariable String paymentUuid,
            @Valid @RequestBody(required = false) CashCollectionRequest request
    ) {
        PaymentResponse response = paymentService.confirmCashCollection(driverPrincipal, paymentUuid, request);
        return ResponseEntity.ok(response);
    }
}
