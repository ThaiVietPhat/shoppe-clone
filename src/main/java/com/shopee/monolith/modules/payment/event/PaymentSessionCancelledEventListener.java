package com.shopee.monolith.modules.payment.event;

import com.shopee.monolith.modules.order.event.CheckoutSessionCancelledEvent;
import com.shopee.monolith.modules.payment.model.PaymentAttemptStatus;
import com.shopee.monolith.modules.payment.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSessionCancelledEventListener {

    private static final List<PaymentAttemptStatus> TERMINAL_STATUSES = Arrays.stream(PaymentAttemptStatus.values())
            .filter(PaymentAttemptStatus::isTerminal)
            .toList();

    private final PaymentAttemptRepository paymentAttemptRepository;

    /**
     * Runs synchronously in the same thread after the cancel transaction commits.
     * REQUIRES_NEW: cancel session lock is already released, so no deadlock with webhook/timeout.
     * Uses a bulk UPDATE that bypasses @Version to avoid OptimisticLockException.
     * Exceptions are swallowed — cancel already committed; next webhook/timeout will handle stale attempt.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCheckoutSessionCancelled(CheckoutSessionCancelledEvent event) {
        try {
            int expired = paymentAttemptRepository.expireNonTerminalByCheckoutSession(
                    event.sessionId(), PaymentAttemptStatus.EXPIRED, TERMINAL_STATUSES);
            if (expired > 0) {
                log.info("Expired {} pending attempt(s) for cancelled session {}", expired, event.sessionId());
            }
        } catch (Exception e) {
            log.warn("Failed to expire attempts for cancelled session {} — will be resolved by next webhook or timeout",
                    event.sessionId(), e);
        }
    }
}
