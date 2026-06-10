package com.shopee.monolith.modules.payment.service;

import com.shopee.monolith.modules.order.service.CheckoutSettlementService;
import com.shopee.monolith.modules.payment.entity.PaymentAttempt;
import com.shopee.monolith.modules.payment.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Expires a single overdue ONLINE payment attempt in its own short transaction.
 * COD attempts (PENDING_COD) are never scanned — COD is settled at initiation.
 * Locks the attempt with SKIP LOCKED so a concurrent success webhook simply wins.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutProcessor {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final CheckoutSettlementService checkoutSettlementService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processTimeout(UUID attemptId, Instant now) {
        PaymentAttempt attempt = paymentAttemptRepository.findNonTerminalByIdForUpdateSkipLocked(attemptId)
                .orElse(null);
        if (attempt == null) {
            log.info("Payment attempt {} already terminal or locked — skipping timeout", attemptId);
            return;
        }
        if (attempt.getExpiresAt().isAfter(now)) {
            return;
        }

        attempt.expire();
        paymentAttemptRepository.save(attempt);
        checkoutSettlementService.markCheckoutPaymentExpired(attempt.getCheckoutSessionId());
        log.info("Payment attempt {} expired; checkout session {} released",
                attemptId, attempt.getCheckoutSessionId());
    }
}
