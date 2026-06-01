package com.shopee.monolith.modules.auth.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AccessTokenBlacklistServiceImpl implements AccessTokenBlacklistService {

    private static final String BLACKLIST_KEY_PREFIX = "security:blacklist:";
    private static final String BLACKLISTED_VALUE = "1";

    private final StringRedisTemplate stringRedisTemplate;
    private final Clock clock;

    @Override
    public void blacklist(AccessTokenClaims claims) {
        if (claims == null || claims.jti() == null || claims.jti().trim().isEmpty() || claims.expiresAt() == null) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        try {
            Instant now = clock.instant();
            Duration ttl = Duration.between(now, claims.expiresAt());
            if (ttl.isNegative() || ttl.isZero()) {
                return;
            }

            String key = BLACKLIST_KEY_PREFIX + claims.jti();
            stringRedisTemplate.opsForValue().set(key, BLACKLISTED_VALUE, ttl);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.trim().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        try {
            String key = BLACKLIST_KEY_PREFIX + jti;
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
        } catch (Exception e) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
