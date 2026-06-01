package com.shopee.monolith.modules.auth.scheduler;

import com.shopee.monolith.modules.auth.config.RefreshTokenCleanupProperties;
import com.shopee.monolith.modules.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCleanupProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.security.refresh-token-cleanup.fixed-delay:1h}")
    public void cleanupExpiredTokens() {
        log.info("Starting expired refresh tokens cleanup job...");
        try {
            Instant now = clock.instant();
            int deleted = refreshTokenService.deleteExpiredTokensBatch(now, properties.getBatchSize());
            log.info("Finished expired refresh tokens cleanup job. Deleted: {}", deleted);
        } catch (Exception e) {
            log.error("Error occurred during expired refresh tokens cleanup", e);
        }
    }
}
