package com.shopee.monolith.modules.auth.service;

import com.shopee.monolith.BasePostgresRedisIntegrationTest;
import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessTokenBlacklistServiceIT extends BasePostgresRedisIntegrationTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private AccessTokenBlacklistService blacklistService;

    private Clock testClock;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.now();
        testClock = Clock.fixed(now, ZoneId.systemDefault());
    }

    @Test
    void blacklistAndIsBlacklistedShouldWorkAsExpected() throws InterruptedException {
        String jti = UUID.randomUUID().toString();
        AccessTokenClaims claims = AccessTokenClaims.builder()
                .jti(jti)
                .expiresAt(now.plusSeconds(5)) // short TTL for testing
                .build();

        // 1. Blacklist the token
        // Use a service configured with custom clock so TTL matches precisely
        AccessTokenBlacklistService customClockService = new AccessTokenBlacklistServiceImpl(stringRedisTemplate, testClock);
        customClockService.blacklist(claims);

        // 2. Read state: should be blacklisted
        assertTrue(customClockService.isBlacklisted(jti));

        // 3. Verify key format, value and TTL in Redis
        String key = "security:blacklist:" + jti;
        assertEquals("1", stringRedisTemplate.opsForValue().get(key));

        Long ttl = stringRedisTemplate.getExpire(key);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 5);

        // 4. Token expired logic: blacklist expired claim should not write to Redis
        String expiredJti = UUID.randomUUID().toString();
        AccessTokenClaims expiredClaims = AccessTokenClaims.builder()
                .jti(expiredJti)
                .expiresAt(now.minusSeconds(1))
                .build();

        customClockService.blacklist(expiredClaims);
        assertFalse(customClockService.isBlacklisted(expiredJti));
    }

    @Test
    void blacklistWhenCalledRepeatedlyShouldBeIdempotentAndNotCreateExtraKeys() {
        String jti = UUID.randomUUID().toString();
        AccessTokenClaims claims = AccessTokenClaims.builder()
                .jti(jti)
                .expiresAt(now.plusSeconds(300))
                .build();

        AccessTokenBlacklistService customClockService = new AccessTokenBlacklistServiceImpl(stringRedisTemplate, testClock);

        // Call twice
        customClockService.blacklist(claims);
        customClockService.blacklist(claims);

        // Should be blacklisted
        assertTrue(customClockService.isBlacklisted(jti));

        // Verify only 1 key exists for this pattern
        Set<String> keys = stringRedisTemplate.keys("security:blacklist:" + jti);
        assertNotNull(keys);
        assertEquals(1, keys.size());
    }

    @Test
    void whenRedisUnavailableShouldThrowServiceUnavailable() throws java.io.IOException {
        int closedPort;
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        LettuceConnectionFactory connectionFactory = null;
        try {
            // Instantiate a disconnected ConnectionFactory pointing to an unused port
            connectionFactory = new LettuceConnectionFactory("localhost", closedPort);
            connectionFactory.afterPropertiesSet();

            StringRedisTemplate disconnectedTemplate = new StringRedisTemplate(connectionFactory);
            AccessTokenBlacklistService brokenService = new AccessTokenBlacklistServiceImpl(disconnectedTemplate, testClock);

            AccessTokenClaims claims = AccessTokenClaims.builder()
                    .jti("jti-fails")
                    .expiresAt(now.plusSeconds(300))
                    .build();

            // 1. Verify blacklist writes fail with SERVICE_UNAVAILABLE
            AppException writeEx = assertThrows(AppException.class, () -> brokenService.blacklist(claims));
            assertEquals(ErrorCode.SERVICE_UNAVAILABLE, writeEx.getErrorCode());

            // 2. Verify blacklist checks fail with SERVICE_UNAVAILABLE
            AppException readEx = assertThrows(AppException.class, () -> brokenService.isBlacklisted("jti-fails"));
            assertEquals(ErrorCode.SERVICE_UNAVAILABLE, readEx.getErrorCode());
        } finally {
            if (connectionFactory != null) {
                // Clean up connection factory to release resources
                connectionFactory.destroy();
            }
        }
    }
}
