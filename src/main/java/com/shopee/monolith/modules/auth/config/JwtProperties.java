package com.shopee.monolith.modules.auth.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Validated
@Getter
@Setter
public class JwtProperties {

    @NotBlank
    private String secret;

    @NotNull
    private Duration expiration;

    @NotNull
    private Duration refreshExpiration;

    @PostConstruct
    public void validateProperties() {
        if (secret != null && secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes (256 bits) for HS256");
        }
        if (expiration == null || expiration.isNegative() || expiration.isZero()) {
            throw new IllegalStateException("JWT expiration must be greater than zero");
        }
        if (refreshExpiration == null || refreshExpiration.isNegative() || refreshExpiration.isZero()) {
            throw new IllegalStateException("JWT refresh expiration must be greater than zero");
        }
    }
}
