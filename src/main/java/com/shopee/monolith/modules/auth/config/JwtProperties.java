package com.shopee.monolith.modules.auth.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Validated
@Getter
@Setter
public class JwtProperties {

    @NotBlank
    private String issuer;

    @NotBlank
    private String audience;

    @NotNull
    private Duration expiration;

    @NotNull
    private Duration refreshExpiration;

    @NotNull
    private KeyRingProperties keyRing = new KeyRingProperties();

    @Getter
    @Setter
    public static class KeyRingProperties {
        @NotBlank
        private String activeKeyId;
        @NotEmpty
        private Map<String, String> keys;
    }

    @PostConstruct
    public void validateProperties() {
        if (expiration == null || expiration.isNegative() || expiration.isZero()) {
            throw new IllegalStateException("JWT expiration must be greater than zero");
        }
        if (refreshExpiration == null || refreshExpiration.isNegative() || refreshExpiration.isZero()) {
            throw new IllegalStateException("JWT refresh expiration must be greater than zero");
        }
        String activeKey = keyRing.getKeys().get(keyRing.getActiveKeyId());
        if (activeKey == null || activeKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Active key value must exist and be at least 32 bytes");
        }
        for (Map.Entry<String, String> entry : keyRing.getKeys().entrySet()) {
            if (entry.getValue() != null && entry.getValue().getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException("Key " + entry.getKey() + " must be at least 32 bytes");
            }
        }
    }
}
