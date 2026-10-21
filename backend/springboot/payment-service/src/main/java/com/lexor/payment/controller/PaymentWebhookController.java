package com.lexor.payment.controller;

import com.lexor.payment.service.IPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments/webhooks")
@RequiredArgsConstructor
@Tag(name = "Payment Webhooks", description = "Asynchronous callback ingress for external payment gateway event notifications (e.g., Razorpay)")
public class PaymentWebhookController {

    private final IPaymentService paymentService;

    @Operation(summary = "Ingest payment gateway webhook", description = "Verifies cryptographic webhook signature, deduplicates event, and updates transaction state")
    @PostMapping("/{provider}")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @PathVariable String provider,
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String razorpaySignature,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String genericSignature
    ) {
        String signature = razorpaySignature != null ? razorpaySignature : genericSignature;
        paymentService.processWebhook(provider, rawPayload, signature);
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Webhook processed successfully"));
    }
}
