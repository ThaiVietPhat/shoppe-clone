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

    @NotBlank
    private String activeKeyId;

    @NotBlank
    private String activeSecret;

    private String previousKeyId;
    private String previousSecret;

    @NotNull
    private KeyRingProperties keyRing = new KeyRingProperties();

    @Getter
    @Setter
    public static class KeyRingProperties {
        private String activeKeyId;
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

        // If keyRing was set manually (e.g. in unit tests) and flat properties are blank, sync them
        if ((activeKeyId == null || activeKeyId.isBlank()) && keyRing != null && keyRing.getActiveKeyId() != null) {
            activeKeyId = keyRing.getActiveKeyId();
            if (keyRing.getKeys() != null) {
                activeSecret = keyRing.getKeys().get(activeKeyId);
            }
        }

        if (activeKeyId == null || activeKeyId.isBlank()) {
            throw new IllegalStateException("Active key ID must not be blank");
        }
        if (activeSecret == null || activeSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Active key value must exist and be at least 32 bytes");
        }

        keyRing.setActiveKeyId(activeKeyId);
        Map<String, String> keysMap = new java.util.HashMap<>();
        keysMap.put(activeKeyId, activeSecret);

        if (previousKeyId != null && !previousKeyId.isBlank() && previousSecret != null && !previousSecret.isBlank()) {
            if (previousSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException("Key " + previousKeyId + " must be at least 32 bytes");
            }
            keysMap.put(previousKeyId, previousSecret);
        } else if (keyRing != null && keyRing.getKeys() != null) {
            // Sync any other keys that were set manually in the keyRing
            for (Map.Entry<String, String> entry : keyRing.getKeys().entrySet()) {
                if (!entry.getKey().equals(activeKeyId)) {
                    if (entry.getValue() != null && entry.getValue().getBytes(StandardCharsets.UTF_8).length < 32) {
                        throw new IllegalStateException("Key " + entry.getKey() + " must be at least 32 bytes");
                    }
                    keysMap.put(entry.getKey(), entry.getValue());
                }
            }
        }

        keyRing.setKeys(keysMap);
    }
}
