package com.shopee.monolith.modules.order.service;

import com.shopee.monolith.modules.order.model.CheckoutSessionStatus;
import com.shopee.monolith.modules.order.repository.CheckoutSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.PageRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutTimeoutService {

    private final CheckoutSessionRepository checkoutSessionRepository;
    private final CheckoutTimeoutProcessor timeoutProcessor;
    private final com.shopee.monolith.common.observability.DemoMetrics demoMetrics;

    public void processExpiredCheckouts(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Checkout timeout batch size must be greater than zero");
        }
        Instant now = Instant.now();
        List<UUID> expiredSessionIds = checkoutSessionRepository.findExpiredIds(
                CheckoutSessionStatus.PENDING_PAYMENT,
                now,
                PageRequest.of(0, batchSize)
        );

        if (expiredSessionIds.isEmpty()) {
            return;
        }

        log.info("Found {} expired checkout sessions to process", expiredSessionIds.size());
        demoMetrics.incrementSchedulerProcessed("checkout-timeout", expiredSessionIds.size());

        for (UUID sessionId : expiredSessionIds) {
            try {
                timeoutProcessor.processTimeout(sessionId, now);
            } catch (Exception e) {
                log.error("Failed to process timeout for session: " + sessionId, e);
            }
        }
    }
}
