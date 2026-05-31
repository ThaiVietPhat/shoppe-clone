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

    @Test
    void dummyHashShouldBeValidBCryptFormat() {
        String dummyHash = "$2a$12$6yGZ/X4sF.FhPUp1p.2KFeZpG.0u4hW1.c.4zY5P6q7r8s9t0u1v2";
        
        assertTrue(dummyHash.startsWith("$2a$12$") || dummyHash.startsWith("$2b$12$") || dummyHash.startsWith("$2y$12$"));
        
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            boolean matches = passwordEncoder.matches("anyRawPassword", dummyHash);
            org.junit.jupiter.api.Assertions.assertFalse(matches);
        });
    }
}
