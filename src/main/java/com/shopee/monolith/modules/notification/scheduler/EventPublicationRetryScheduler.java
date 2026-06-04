package com.shopee.monolith.modules.notification.scheduler;

import com.shopee.monolith.modules.notification.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublicationRetryScheduler {

    private final IncompleteEventPublications incompleteEventPublications;
    private final NotificationProperties properties;

    @Scheduled(fixedDelayString = "${app.notification.retry.fixed-delay}")
    public void retryFailedPublications() {
        log.info("Starting retry of failed event publications...");
        AtomicInteger count = new AtomicInteger(0);
        int limit = properties.getRetry().getBatchSize();

        try {
            incompleteEventPublications.resubmitIncompletePublications(pub -> {
                if (count.get() >= limit) {
                    return false;
                }
                count.incrementAndGet();
                return true;
            });
            log.info("Finished retry of failed event publications. Resubmitted: {} publications", count.get());
        } catch (Exception e) {
            log.error("Failed to retry incomplete event publications", e);
        }
    }
}
