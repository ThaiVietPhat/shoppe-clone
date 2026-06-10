package com.shopee.monolith.modules.payment.scheduler;

import com.shopee.monolith.modules.payment.config.PaymentTimeoutProperties;
import com.shopee.monolith.modules.payment.service.PaymentTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutScheduler {

    private final PaymentTimeoutService timeoutService;
    private final PaymentTimeoutProperties properties;

    @Scheduled(fixedDelayString = "#{@paymentTimeoutProperties.fixedDelay.toMillis()}")
    public void runTimeoutJob() {
        try {
            timeoutService.processExpiredAttempts(properties.getBatchSize());
        } catch (Exception e) {
            log.error("Error occurred while executing payment timeout scheduler job", e);
        }
    }
}
