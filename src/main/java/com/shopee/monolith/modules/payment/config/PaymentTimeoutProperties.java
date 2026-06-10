package com.shopee.monolith.modules.payment.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Configuration("paymentTimeoutProperties")
@ConfigurationProperties(prefix = "app.payment.timeout")
@Validated
@Getter
@Setter
public class PaymentTimeoutProperties {

    /** Lifetime of an ONLINE payment attempt before the timeout job expires it. */
    @Min(1)
    private int attemptTimeoutMinutes = 15;

    @Min(1)
    private int batchSize = 50;

    @NotNull
    private Duration fixedDelay = Duration.ofSeconds(30);

    @PostConstruct
    public void validate() {
        if (fixedDelay == null || fixedDelay.isNegative() || fixedDelay.isZero()) {
            throw new IllegalStateException("Payment timeout fixed delay must be greater than zero");
        }
    }
}
