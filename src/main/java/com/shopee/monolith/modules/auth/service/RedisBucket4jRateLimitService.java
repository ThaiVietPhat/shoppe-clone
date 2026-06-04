package com.shopee.monolith.modules.auth.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.config.AuthSecurityProperties.BucketLimitProperties;
import com.shopee.monolith.modules.auth.dto.internal.RateLimitResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisBucket4jRateLimitService implements RateLimitService {

    private final ProxyManager<byte[]> proxyManager;

    @Override
    public RateLimitResult consume(String key, BucketLimitProperties limitProperties) {
        if (key == null || limitProperties == null) {
            throw new IllegalArgumentException("Key and limit properties must not be null");
        }

        try {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
                    .addLimit(Bandwidth.classic(
                            limitProperties.getCapacity(),
                            Refill.greedy(limitProperties.getCapacity(), limitProperties.getWindow())))
                    .build();

            var bucket = proxyManager.builder().build(keyBytes, configSupplier);
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

            return new RateLimitResult(probe.isConsumed(), probe.getRemainingTokens());
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis connection error during rate limit check for key: {}", maskKeyForLogging(key), e);
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    private String maskKeyForLogging(String key) {
        if (key == null) {
            return "null";
        }
        String[] parts = key.split(":");
        if (parts.length > 2) {
            return parts[0] + ":" + parts[1] + ":***";
        }
        return "***";
    }
}
