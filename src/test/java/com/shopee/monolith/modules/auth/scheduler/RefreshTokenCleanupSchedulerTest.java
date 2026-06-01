package com.shopee.monolith.modules.auth.scheduler;

import com.shopee.monolith.modules.auth.config.RefreshTokenCleanupProperties;
import com.shopee.monolith.modules.auth.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupSchedulerTest {

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private RefreshTokenCleanupProperties properties;

    private Clock clock;
    private Instant now;
    private RefreshTokenCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        now = Instant.now();
        clock = Clock.fixed(now, ZoneId.systemDefault());
        scheduler = new RefreshTokenCleanupScheduler(refreshTokenService, properties, clock);
    }

    @Test
    void cleanupExpiredTokensShouldCallServiceWithConfiguredBatchSize() {
        when(properties.getBatchSize()).thenReturn(100);

        scheduler.cleanupExpiredTokens();

        verify(refreshTokenService).deleteExpiredTokensBatch(now, 100);
    }

    @Test
    void cleanupExpiredTokensShouldHandleExceptionsGracefully() {
        when(properties.getBatchSize()).thenReturn(100);
        doThrow(new RuntimeException("Database error"))
                .when(refreshTokenService).deleteExpiredTokensBatch(any(), eq(100));

        // Should not propagate exception
        scheduler.cleanupExpiredTokens();

        verify(refreshTokenService).deleteExpiredTokensBatch(now, 100);
    }
}
