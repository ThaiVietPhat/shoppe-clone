package com.shopee.monolith.modules.payment.service;

import com.shopee.monolith.modules.payment.model.PaymentAttemptStatus;
import com.shopee.monolith.modules.payment.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTimeoutService {

    private static final Set<PaymentAttemptStatus> EXPIRABLE_STATUSES = Set.of(
            PaymentAttemptStatus.CREATED,
            PaymentAttemptStatus.INITIATING,
            PaymentAttemptStatus.PENDING
    );

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentTimeoutProcessor timeoutProcessor;

    public void processExpiredAttempts(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Payment timeout batch size must be greater than zero");
        }
        Instant now = Instant.now();
        List<UUID> expiredIds = paymentAttemptRepository.findExpiredIds(
                EXPIRABLE_STATUSES, now, PageRequest.of(0, batchSize));
        if (expiredIds.isEmpty()) {
            return;
        }
        log.info("Found {} expired payment attempts to process", expiredIds.size());
        for (UUID attemptId : expiredIds) {
            try {
                timeoutProcessor.processTimeout(attemptId, now);
            } catch (Exception e) {
                log.error("Failed to process payment timeout for attempt: " + attemptId, e);
            }
        }
    }
}
