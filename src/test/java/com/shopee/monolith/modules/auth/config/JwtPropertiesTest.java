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
        jwtProperties.setSecret("super-secret-key-that-is-at-least-32-bytes-long");
        jwtProperties.setExpiration(Duration.ofMinutes(10));
        jwtProperties.setRefreshExpiration(Duration.ofDays(7));
    }

    @Test
    void validatePropertiesShouldPassForValidConfig() {
        assertDoesNotThrow(() -> jwtProperties.validateProperties());
    }

    @Test
    void validatePropertiesShouldThrowIfSecretIsTooShort() {
        jwtProperties.setSecret("too-short");
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> jwtProperties.validateProperties());
        assertEquals("JWT secret must be at least 32 bytes (256 bits) for HS256", exception.getMessage());
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
