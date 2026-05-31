package com.shopee.monolith.modules.auth.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthConfigTest {

    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        AuthConfig authConfig = new AuthConfig();
        passwordEncoder = authConfig.passwordEncoder();
    }

    @Test
    void passwordEncoderShouldMatchPasswordCorrectly() {
        String rawPassword = "mySecretPassword";
        String hash = passwordEncoder.encode(rawPassword);

        assertTrue(passwordEncoder.matches(rawPassword, hash));
    }

    @Test
    void passwordEncoderShouldUseCostTwelve() {
        String rawPassword = "mySecretPassword";
        String hash = passwordEncoder.encode(rawPassword);

        assertTrue(hash.startsWith("$2a$12$") || hash.startsWith("$2b$12$") || hash.startsWith("$2y$12$"));
    }

}
