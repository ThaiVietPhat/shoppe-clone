package com.shopee.monolith.modules.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTokenGeneratorTest {

    private RefreshTokenGenerator refreshTokenGenerator;
    private static final Pattern URL_SAFE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    @BeforeEach
    void setUp() {
        refreshTokenGenerator = new RefreshTokenGenerator();
    }

    @Test
    void generatedTokensShouldNotBeEqual() {
        String token1 = refreshTokenGenerator.generate();
        String token2 = refreshTokenGenerator.generate();

        assertNotNull(token1);
        assertNotNull(token2);
        assertNotEquals(token1, token2);
    }

    @Test
    void generatedTokensShouldBeUrlSafeAndHaveNoPadding() {
        String token = refreshTokenGenerator.generate();

        assertNotNull(token);
        assertFalse(token.contains("="), "Token should not contain padding character '='");
        assertFalse(token.contains("+"), "Token should not contain character '+'");
        assertFalse(token.contains("/"), "Token should not contain character '/'");
        assertTrue(URL_SAFE_PATTERN.matcher(token).matches(), "Token should contain only URL-safe Base64 characters");
    }

    @Test
    void hashShouldBeDeterministic() {
        String token = refreshTokenGenerator.generate();

        String hash1 = refreshTokenGenerator.hash(token);
        String hash2 = refreshTokenGenerator.hash(token);

        assertEquals(hash1, hash2);
        assertNotEquals(token, hash1);
    }

    @Test
    void differentRawTokensShouldProduceDifferentHashes() {
        String token1 = refreshTokenGenerator.generate();
        String token2 = refreshTokenGenerator.generate();

        String hash1 = refreshTokenGenerator.hash(token1);
        String hash2 = refreshTokenGenerator.hash(token2);

        assertNotEquals(hash1, hash2);
    }

    @Test
    void hashShouldNotBeEqualToRawToken() {
        String token = refreshTokenGenerator.generate();
        String hash = refreshTokenGenerator.hash(token);

        assertNotEquals(token, hash);
    }

    @Test
    void hashShouldThrowExceptionForNullOrBlankToken() {
        assertThrows(IllegalArgumentException.class, () -> refreshTokenGenerator.hash(null));
        assertThrows(IllegalArgumentException.class, () -> refreshTokenGenerator.hash(""));
        assertThrows(IllegalArgumentException.class, () -> refreshTokenGenerator.hash("   "));
    }
}
