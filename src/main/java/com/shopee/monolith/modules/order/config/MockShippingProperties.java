package com.shopee.monolith.modules.order.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Configuration
@ConfigurationProperties(prefix = "app.checkout.mock-shipping")
@Validated
@Getter
@Setter
public class MockShippingProperties {

    private BigDecimal flatFeePerShop = BigDecimal.valueOf(30000);

    @PostConstruct
    public void validate() {
        if (flatFeePerShop == null || flatFeePerShop.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("app.checkout.mock-shipping.flat-fee-per-shop must be >= 0");
        }
    }
}
