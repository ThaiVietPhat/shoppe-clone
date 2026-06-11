package com.shopee.monolith.modules.payment.service;

import com.shopee.monolith.modules.order.service.PaymentCancellationPort;
import com.shopee.monolith.modules.payment.entity.PaymentAttempt;
import com.shopee.monolith.modules.payment.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCancellationPortImpl implements PaymentCancellationPort {

    private final PaymentAttemptRepository paymentAttemptRepository;

    @Override
    @Transactional
    public void expirePendingAttemptsForSession(UUID checkoutSessionId) {
        List<PaymentAttempt> pending = paymentAttemptRepository
                .findAllByCheckoutSessionIdOrderByCreatedAtDesc(checkoutSessionId)
                .stream()
                .filter(a -> !a.getStatus().isTerminal())
                .toList();

        if (pending.isEmpty()) {
            return;
        }

        pending.forEach(PaymentAttempt::expire);
        paymentAttemptRepository.saveAll(pending);
        log.info("Expired {} pending payment attempt(s) for cancelled session {}", pending.size(), checkoutSessionId);
    }
}
