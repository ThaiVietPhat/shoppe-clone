package com.shopee.monolith.modules.auth.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessTokenBlacklistServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private Clock clock;
    private Instant now;
    private AccessTokenBlacklistService blacklistService;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-06-01T00:00:00Z");
        clock = Clock.fixed(now, ZoneId.of("UTC"));
        blacklistService = new AccessTokenBlacklistServiceImpl(stringRedisTemplate, clock);
    }

    @Test
    void blacklistWhenTokenActiveShouldWriteExpectedKeyValueAndRemainingTtl() {
        // Given
        AccessTokenClaims claims = AccessTokenClaims.builder()
                .jti("active-jti")
                .expiresAt(now.plusSeconds(300))
                .build();
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        blacklistService.blacklist(claims);

        // Then
        verify(valueOperations).set("security:blacklist:active-jti", "1", Duration.ofSeconds(300));
    }

    @Test
    void blacklistWhenTokenExpiredShouldNotWriteRedis() {
        // Given
        AccessTokenClaims claims = AccessTokenClaims.builder()
                .jti("expired-jti")
                .expiresAt(now.minusSeconds(10))
                .build();

        // When
        blacklistService.blacklist(claims);

        // Then
        verify(stringRedisTemplate, never()).opsForValue();
    }

    @Test
    void blacklistWhenCalledRepeatedlyShouldRemainIdempotent() {
        // Given
        AccessTokenClaims claims = AccessTokenClaims.builder()
                .jti("idempotent-jti")
                .expiresAt(now.plusSeconds(100))
                .build();
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        blacklistService.blacklist(claims);
        blacklistService.blacklist(claims);

        // Then
        verify(valueOperations, times(2)).set("security:blacklist:idempotent-jti", "1", Duration.ofSeconds(100));
    }

    @Test
    void blacklistWhenClaimsOrJtiInvalidShouldThrowInvalidToken() {
        // Given & Then
        AppException ex1 = assertThrows(AppException.class, () -> blacklistService.blacklist(null));
        assertEquals(ErrorCode.INVALID_TOKEN, ex1.getErrorCode());

        AccessTokenClaims claimsNullJti = AccessTokenClaims.builder()
                .jti(null)
                .expiresAt(now.plusSeconds(300))
                .build();
        AppException ex2 = assertThrows(AppException.class, () -> blacklistService.blacklist(claimsNullJti));
        assertEquals(ErrorCode.INVALID_TOKEN, ex2.getErrorCode());

        AccessTokenClaims claimsBlankJti = AccessTokenClaims.builder()
                .jti("   ")
                .expiresAt(now.plusSeconds(300))
                .build();
        AppException ex3 = assertThrows(AppException.class, () -> blacklistService.blacklist(claimsBlankJti));
        assertEquals(ErrorCode.INVALID_TOKEN, ex3.getErrorCode());

        AccessTokenClaims claimsNullExpiry = AccessTokenClaims.builder()
                .jti("some-jti")
                .expiresAt(null)
                .build();
        AppException ex4 = assertThrows(AppException.class, () -> blacklistService.blacklist(claimsNullExpiry));
        assertEquals(ErrorCode.INVALID_TOKEN, ex4.getErrorCode());
    }

    @Test
    void blacklistWhenRedisUnavailableShouldThrowServiceUnavailable() {
        // Given
        AccessTokenClaims claims = AccessTokenClaims.builder()
                .jti("failed-jti")
                .expiresAt(now.plusSeconds(300))
                .build();
        when(stringRedisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("Connection failed"));

        // When & Then
        AppException ex = assertThrows(AppException.class, () -> blacklistService.blacklist(claims));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, ex.getErrorCode());
    }

    @Test
    void isBlacklistedWhenKeyExistsShouldReturnTrue() {
        // Given
        when(stringRedisTemplate.hasKey("security:blacklist:existing-jti")).thenReturn(true);

        // When
        boolean result = blacklistService.isBlacklisted("existing-jti");

        // Then
        assertTrue(result);
        verify(stringRedisTemplate).hasKey("security:blacklist:existing-jti");
    }

    @Test
    void isBlacklistedWhenKeyMissingShouldReturnFalse() {
        // Given
        when(stringRedisTemplate.hasKey("security:blacklist:missing-jti")).thenReturn(false);

        // When
        boolean result = blacklistService.isBlacklisted("missing-jti");

        // Then
        assertFalse(result);
        verify(stringRedisTemplate).hasKey("security:blacklist:missing-jti");
    }

    @Test
    void isBlacklistedWhenRedisUnavailableShouldThrowServiceUnavailable() {
        // Given
        when(stringRedisTemplate.hasKey("security:blacklist:failed-jti"))
                .thenThrow(new RedisConnectionFailureException("Connection failed"));

        // When & Then
        AppException ex = assertThrows(AppException.class, () -> blacklistService.isBlacklisted("failed-jti"));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, ex.getErrorCode());
    }

    @Test
    void isBlacklistedWhenJtiBlankShouldThrowInvalidToken() {
        // Given & Then
        AppException ex1 = assertThrows(AppException.class, () -> blacklistService.isBlacklisted(null));
        assertEquals(ErrorCode.INVALID_TOKEN, ex1.getErrorCode());

        AppException ex2 = assertThrows(AppException.class, () -> blacklistService.isBlacklisted("   "));
        assertEquals(ErrorCode.INVALID_TOKEN, ex2.getErrorCode());
    }
}
