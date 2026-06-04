package com.shopee.monolith.modules.auth.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

@Configuration
public class RateLimitConfig {

    @Bean
    public ProxyManager<byte[]> lettuceBasedProxyManager(
            LettuceConnectionFactory connectionFactory,
            AuthSecurityProperties properties) {
        Object nativeClient = connectionFactory.getNativeClient();
        if (!(nativeClient instanceof RedisClient)) {
            throw new IllegalStateException("LettuceConnectionFactory native client must be an instance of io.lettuce.core.RedisClient");
        }
        RedisClient redisClient = (RedisClient) nativeClient;

        Duration maxWindow = Duration.ofMinutes(1); // default fallback
        var rateLimit = properties.getRateLimit();
        if (rateLimit != null) {
            maxWindow = max(maxWindow, rateLimit.getAnonymous().getWindow());
            maxWindow = max(maxWindow, rateLimit.getAuthenticated().getWindow());
            maxWindow = max(maxWindow, rateLimit.getLogin().getWindow());
            maxWindow = max(maxWindow, rateLimit.getRegister().getWindow());
            maxWindow = max(maxWindow, rateLimit.getVerify().getWindow());
            maxWindow = max(maxWindow, rateLimit.getOauth2Callback().getWindow());
            maxWindow = max(maxWindow, rateLimit.getResendVerification().getWindow());
        }

        // Add 5 minutes cushion to ensure Redis doesn't expire bucket state too early
        Duration expirationTtl = maxWindow.plus(Duration.ofMinutes(5));

        return LettuceBasedProxyManager.builderFor(redisClient)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(expirationTtl))
                .build();
    }

    private Duration max(Duration d1, Duration d2) {
        if (d1 == null) {
            return d2;
        }
        if (d2 == null) {
            return d1;
        }
        return d1.compareTo(d2) > 0 ? d1 : d2;
    }
}
