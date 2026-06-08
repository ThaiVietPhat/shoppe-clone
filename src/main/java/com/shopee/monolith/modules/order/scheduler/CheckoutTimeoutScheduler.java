package com.shopee.monolith.modules.order.scheduler;

import com.shopee.monolith.modules.order.config.CheckoutTimeoutProperties;
import com.shopee.monolith.modules.order.service.CheckoutTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheckoutTimeoutScheduler {

    private final CheckoutTimeoutService timeoutService;
    private final CheckoutTimeoutProperties properties;

    @Scheduled(fixedDelayString = "#{@checkoutTimeoutProperties.checkDelay.toMillis()}")
    public void runTimeoutJob() {
        try {
            timeoutService.processExpiredCheckouts(properties.getBatchSize());
        } catch (Exception e) {
            log.error("Error occurred while executing checkout timeout scheduler job", e);
        }
    }
}
