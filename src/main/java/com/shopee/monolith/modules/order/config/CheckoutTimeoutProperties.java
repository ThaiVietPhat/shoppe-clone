package com.shopee.monolith.modules.order.config;

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
@ConfigurationProperties(prefix = "app.order.timeout")
@Validated
@Getter
@Setter
public class CheckoutTimeoutProperties {

    @Min(1)
    private int batchSize = 50;

    @NotNull
    private Duration checkDelay = Duration.ofSeconds(10);

    @PostConstruct
    public void validate() {
        if (batchSize <= 0) {
            throw new IllegalStateException("Checkout timeout batch size must be greater than zero");
        }
        if (checkDelay == null || checkDelay.isNegative() || checkDelay.isZero()) {
            throw new IllegalStateException("Checkout timeout check delay must be greater than zero");
        }
    }
}
