package com.shopee.monolith.modules.auth.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "app.security.refresh-token-cleanup")
@Validated
@Getter
@Setter
public class RefreshTokenCleanupProperties {

    @Min(1)
    private int batchSize = 500;

    @NotNull
    private Duration fixedDelay = Duration.ofHours(1);

    @PostConstruct
    public void validate() {
        if (batchSize <= 0) {
            throw new IllegalStateException("Cleanup batch size must be greater than zero");
        }
        if (fixedDelay == null || fixedDelay.isNegative() || fixedDelay.isZero()) {
            throw new IllegalStateException("Cleanup fixed delay must be greater than zero");
        }
    }
}
