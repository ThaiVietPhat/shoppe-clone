package com.shopee.monolith.modules.cart.config;

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
@ConfigurationProperties(prefix = "app.cart")
@Validated
@Getter
@Setter
public class CartProperties {

    @NotNull
    private Duration ttl = Duration.ofDays(7);

    @Min(1)
    private int maxQuantityPerItem = 99;

    @PostConstruct
    public void validateProperties() {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalStateException("Cart TTL must be positive");
        }
        if (maxQuantityPerItem <= 0) {
            throw new IllegalStateException("Max quantity per item must be positive");
        }
    }
}
