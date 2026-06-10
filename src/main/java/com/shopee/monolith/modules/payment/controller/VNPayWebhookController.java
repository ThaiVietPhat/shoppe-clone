package com.shopee.monolith.modules.payment.controller;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.payment.config.VNPayProperties;
import com.shopee.monolith.modules.payment.repository.PaymentAttemptRepository;
import com.shopee.monolith.modules.payment.service.VNPayWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

/**
 * Provider-facing VNPay endpoints. The IPN webhook is the only authoritative
 * state mutator; the browser return URL never changes payment state.
 * Responses follow the VNPay IPN contract ({"RspCode","Message"}), not ApiResponse.
 */
@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment attempt initiation and buyer-facing payment status APIs")
public class VNPayWebhookController {

    private final VNPayWebhookService webhookService;
    private final VNPayProperties vnPayProperties;
    private final PaymentAttemptRepository paymentAttemptRepository;

    @Operation(
            summary = "VNPay IPN webhook",
            description = "Verifies the HMAC-SHA512 signature before any processing, claims the provider "
                    + "event idempotently and settles the payment attempt. Duplicate events return 200 no-op."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Webhook acknowledged with a VNPay RspCode body.")
    @PostMapping("/webhook/vnpay")
    public ResponseEntity<Map<String, String>> handleWebhook(@RequestParam Map<String, String> params) {
        try {
            webhookService.processWebhook(params);
            return ResponseEntity.ok(Map.of("RspCode", "00", "Message", "Confirm Success"));
        } catch (AppException e) {
            if (e.getErrorCode() == ErrorCode.INVALID_WEBHOOK_SIGNATURE) {
                return ResponseEntity.ok(Map.of("RspCode", "97", "Message", "Invalid Checksum"));
            }
            if (e.getErrorCode() == ErrorCode.PAYMENT_NOT_FOUND) {
                return ResponseEntity.ok(Map.of("RspCode", "01", "Message", "Order not Found"));
            }
            log.error("VNPay webhook processing failed with code {}", e.getErrorCode());
            return ResponseEntity.ok(Map.of("RspCode", "99", "Message", "Unknown error"));
        }
    }

    @Operation(
            summary = "VNPay browser return URL",
            description = "Redirects the buyer's browser to the frontend payment return page with the "
                    + "checkoutSessionId. Performs no state change — only the IPN webhook is authoritative."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "302", description = "Redirect to the frontend payment return page.")
    @GetMapping("/return/vnpay")
    public ResponseEntity<Void> handleReturn(@RequestParam Map<String, String> params) {
        UriComponentsBuilder redirect = UriComponentsBuilder.fromUriString(vnPayProperties.getFrontendReturnUrl());
        resolveCheckoutSessionId(params.get("vnp_TxnRef"))
                .ifPresentOrElse(
                        sessionId -> redirect.queryParam("checkoutSessionId", sessionId),
                        () -> redirect.queryParam("error", "PAYMENT_NOT_FOUND"));
        URI location = redirect.build().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location.toString()).build();
    }

    private java.util.Optional<UUID> resolveCheckoutSessionId(String txnRef) {
        try {
            return paymentAttemptRepository.findById(UUID.fromString(txnRef))
                    .map(attempt -> attempt.getCheckoutSessionId());
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }
}
