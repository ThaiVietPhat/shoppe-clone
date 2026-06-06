package com.shopee.monolith.modules.order.scheduler;

import com.shopee.monolith.modules.order.service.CheckoutTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheckoutTimeoutScheduler {

    private final CheckoutTimeoutService timeoutService;

    @Value("${app.order.timeout.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.order.timeout.check-delay-ms:10000}")
    public void runTimeoutJob() {
        try {
            timeoutService.processExpiredCheckouts(batchSize);
        } catch (Exception e) {
            log.error("Error occurred while executing checkout timeout scheduler job", e);
        }
    }
}
