package com.shopee.monolith.modules.order.service;

import com.shopee.monolith.modules.order.entity.CheckoutSession;
import com.shopee.monolith.modules.order.model.CheckoutSessionStatus;
import com.shopee.monolith.modules.order.repository.CheckoutSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutTimeoutService {

    private final CheckoutSessionRepository checkoutSessionRepository;
    private final CheckoutTimeoutProcessor timeoutProcessor;

    public void processExpiredCheckouts(int batchSize) {
        Instant now = Instant.now();
        List<CheckoutSession> expiredSessions = checkoutSessionRepository.findExpiredForUpdate(
                CheckoutSessionStatus.PENDING_PAYMENT.name(),
                now,
                batchSize
        );

        if (expiredSessions.isEmpty()) {
            return;
        }

        log.info("Found {} expired checkout sessions to process", expiredSessions.size());

        for (CheckoutSession session : expiredSessions) {
            try {
                timeoutProcessor.processTimeout(session.getId(), now);
            } catch (Exception e) {
                log.error("Failed to process timeout for session: " + session.getId(), e);
            }
        }
    }
}
