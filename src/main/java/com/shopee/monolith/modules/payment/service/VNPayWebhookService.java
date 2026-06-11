package com.shopee.monolith.modules.payment.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.order.service.CheckoutSettlementService;
import com.shopee.monolith.modules.payment.entity.PaymentAttempt;
import com.shopee.monolith.modules.payment.model.PaymentAttemptStatus;
import com.shopee.monolith.modules.payment.model.PaymentMethod;
import com.shopee.monolith.modules.payment.repository.PaymentAttemptRepository;
import com.shopee.monolith.modules.payment.repository.PaymentWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Processes VNPay IPN webhooks. Signature is verified before anything else;
 * the (provider, provider_event_id) claim and all payment/order state changes
 * happen in one transaction so a mid-processing failure lets the provider retry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VNPayWebhookService {

    public static final String PROVIDER = "VNPAY";
    private static final String SUCCESS_RESPONSE_CODE = "00";

    private final VNPaySignatureVerifier signatureVerifier;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final CheckoutSettlementService checkoutSettlementService;

    public enum WebhookResult {
        PROCESSED,
        DUPLICATE
    }

    @Transactional
    public WebhookResult processWebhook(Map<String, String> params) {
        if (!signatureVerifier.verify(params)) {
            throw new AppException(ErrorCode.INVALID_WEBHOOK_SIGNATURE);
        }

        PaymentAttempt attempt = lockAttempt(params.get("vnp_TxnRef"));
        String externalTxId = params.getOrDefault("vnp_TransactionNo", "");
        String providerEventId = attempt.getId() + ":" + externalTxId + ":" + params.getOrDefault("vnp_ResponseCode", "");

        int claimed = webhookEventRepository.tryClaim(
                UUID.randomUUID(), PROVIDER, providerEventId, attempt.getId(),
                sha256(signatureVerifier.buildHashData(params)));
        if (claimed == 0) {
            log.info("Duplicate VNPay webhook event {} — no-op", providerEventId);
            return WebhookResult.DUPLICATE;
        }

        if (attempt.getStatus().isTerminal()) {
            if (isLateSuccessWebhook(attempt, params)) {
                attempt.requireReconciliation("LATE_SUCCESS_AFTER_" + attempt.getStatus().name());
                paymentAttemptRepository.save(attempt);
                log.warn("Late VNPay success for {} attempt {} — flagged REQUIRES_RECONCILIATION",
                        attempt.getStatus(), attempt.getId());
            } else {
                log.info("VNPay webhook for terminal attempt {} ({}) — no-op", attempt.getId(), attempt.getStatus());
            }
            return WebhookResult.PROCESSED;
        }

        applyOutcome(attempt, params, externalTxId);
        paymentAttemptRepository.save(attempt);
        return WebhookResult.PROCESSED;
    }

    private void applyOutcome(PaymentAttempt attempt, Map<String, String> params, String externalTxId) {
        if (!amountMatches(attempt, params.get("vnp_Amount"))) {
            attempt.requireReconciliation("AMOUNT_MISMATCH");
            log.warn("VNPay webhook amount mismatch for attempt {} — flagged for reconciliation", attempt.getId());
            return;
        }

        if (SUCCESS_RESPONSE_CODE.equals(params.get("vnp_ResponseCode"))) {
            boolean confirmed = checkoutSettlementService
                    .confirmCheckoutSession(attempt.getCheckoutSessionId(), PaymentMethod.VNPAY.name());
            if (confirmed) {
                attempt.succeed(externalTxId);
            } else {
                // Late success after the session was released/expired — never re-confirm inventory
                attempt.requireReconciliation("SESSION_NOT_PAYABLE");
                log.warn("VNPay success for non-payable session {} — flagged for reconciliation",
                        attempt.getCheckoutSessionId());
            }
        } else {
            attempt.fail(externalTxId);
            checkoutSettlementService.markCheckoutPaymentFailed(attempt.getCheckoutSessionId());
        }
    }

    private boolean isLateSuccessWebhook(PaymentAttempt attempt, Map<String, String> params) {
        return (attempt.getStatus() == PaymentAttemptStatus.FAILED
                || attempt.getStatus() == PaymentAttemptStatus.EXPIRED)
                && SUCCESS_RESPONSE_CODE.equals(params.get("vnp_ResponseCode"))
                && amountMatches(attempt, params.get("vnp_Amount"));
    }

    private boolean amountMatches(PaymentAttempt attempt, String vnpAmount) {
        if (vnpAmount == null || vnpAmount.isBlank()) {
            return false;
        }
        try {
            BigDecimal webhookAmount = new BigDecimal(vnpAmount);
            BigDecimal expected = attempt.getAmount().multiply(BigDecimal.valueOf(100));
            return webhookAmount.compareTo(expected) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private PaymentAttempt lockAttempt(String txnRef) {
        UUID attemptId;
        try {
            attemptId = UUID.fromString(txnRef);
        } catch (Exception e) {
            throw new AppException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        return paymentAttemptRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private String sha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
