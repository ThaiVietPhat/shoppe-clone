package com.shopee.monolith.modules.auth.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtPropertiesTest {

    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setIssuer("shoppe-monolith");
        jwtProperties.setAudience("shoppe-web-client");
        JwtProperties.KeyRingProperties keyRing = new JwtProperties.KeyRingProperties();
        keyRing.setActiveKeyId("key-v1");
        keyRing.setKeys(new java.util.HashMap<>(java.util.Map.of("key-v1", "super-secret-key-that-is-at-least-32-bytes-long")));
        jwtProperties.setKeyRing(keyRing);
        jwtProperties.setExpiration(Duration.ofMinutes(10));
        jwtProperties.setRefreshExpiration(Duration.ofDays(7));
    }

    @Test
    void validatePropertiesShouldPassForValidConfig() {
        assertDoesNotThrow(() -> jwtProperties.validateProperties());
    }

    @Test
    void validatePropertiesShouldThrowIfSecretIsTooShort() {
        jwtProperties.getKeyRing().getKeys().put("key-v1", "too-short");
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> jwtProperties.validateProperties());
        assertEquals("Active key value must exist and be at least 32 bytes", exception.getMessage());
    }

    @Test
    void validatePropertiesShouldThrowIfExpirationIsZero() {
        jwtProperties.setExpiration(Duration.ZERO);
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> jwtProperties.validateProperties());
        assertEquals("JWT expiration must be greater than zero", exception.getMessage());
    }

    @Test
    void validatePropertiesShouldThrowIfExpirationIsNegative() {
        jwtProperties.setExpiration(Duration.ofMinutes(-5));
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> jwtProperties.validateProperties());
        assertEquals("JWT expiration must be greater than zero", exception.getMessage());
    }

    @Test
    void validatePropertiesShouldThrowIfRefreshExpirationIsZero() {
        jwtProperties.setRefreshExpiration(Duration.ZERO);
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> jwtProperties.validateProperties());
        assertEquals("JWT refresh expiration must be greater than zero", exception.getMessage());
    }

    @Test
    void validatePropertiesShouldThrowIfRefreshExpirationIsNegative() {
        jwtProperties.setRefreshExpiration(Duration.ofMinutes(-5));
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> jwtProperties.validateProperties());
        assertEquals("JWT refresh expiration must be greater than zero", exception.getMessage());
    }
}
