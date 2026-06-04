package com.shopee.monolith.modules.auth.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthSecurityPropertiesTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private AuthSecurityProperties createValidProperties() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.getEventCrypto().setActiveSecret("default-event-crypto-secret-32b-key-default-value");
        return properties;
    }

    @Test
    void whenSameSiteIsNoneAndSecureIsFalseShouldFailValidation() {
        AuthSecurityProperties properties = createValidProperties();
        properties.getAuthCookie().setSameSite("None");
        properties.getAuthCookie().setSecure(false);

        Set<ConstraintViolation<AuthSecurityProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("SameSite=None requires Secure=true")));
    }

    @Test
    void whenSameSiteIsNoneAndSecureIsTrueShouldPassValidation() {
        AuthSecurityProperties properties = createValidProperties();
        properties.getAuthCookie().setSameSite("None");
        properties.getAuthCookie().setSecure(true);

        Set<ConstraintViolation<AuthSecurityProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
    }

    @Test
    void whenCorsAllowedOriginsContainsWildcardWithCredentialsShouldFailValidation() {
        AuthSecurityProperties properties = createValidProperties();
        properties.getCors().setAllowCredentials(true);
        properties.getCors().setAllowedOrigins(List.of("http://localhost:3000", "*.domain.com"));

        Set<ConstraintViolation<AuthSecurityProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("CORS allowed origins must be valid URIs and cannot contain path or wildcards if allowCredentials is true")));
    }

    @Test
    void whenCorsAllowedOriginsContainsPathShouldFailValidation() {
        AuthSecurityProperties properties = createValidProperties();
        properties.getCors().setAllowCredentials(false);
        properties.getCors().setAllowedOrigins(List.of("http://localhost:3000/api"));

        Set<ConstraintViolation<AuthSecurityProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
    }

    @Test
    void whenCorsAllowedOriginsIsValidShouldPassValidation() {
        AuthSecurityProperties properties = createValidProperties();
        properties.getCors().setAllowCredentials(true);
        properties.getCors().setAllowedOrigins(List.of("http://localhost:3000", "https://app.shopee.com"));

        Set<ConstraintViolation<AuthSecurityProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
    }

    @Test
    void whenVerificationTokenTtlIsNullShouldFailValidation() {
        AuthSecurityProperties properties = createValidProperties();
        properties.getVerificationToken().setTtl(null);

        Set<ConstraintViolation<AuthSecurityProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
    }

    @Test
    void whenVerificationTokenTtlIsNegativeOrZeroShouldFailValidation() {
        AuthSecurityProperties properties = createValidProperties();

        properties.getVerificationToken().setTtl(java.time.Duration.ofSeconds(-5));
        Set<ConstraintViolation<AuthSecurityProperties>> violations1 = validator.validate(properties);
        assertFalse(violations1.isEmpty());
        assertTrue(violations1.stream().anyMatch(v -> v.getMessage().contains("Verification token TTL must be positive")));

        properties.getVerificationToken().setTtl(java.time.Duration.ZERO);
        Set<ConstraintViolation<AuthSecurityProperties>> violations2 = validator.validate(properties);
        assertFalse(violations2.isEmpty());
        assertTrue(violations2.stream().anyMatch(v -> v.getMessage().contains("Verification token TTL must be positive")));
    }

    @Test
    void whenEventCryptoActiveSecretIsTooShortShouldFailValidation() {
        AuthSecurityProperties properties = createValidProperties();
        properties.getEventCrypto().setActiveSecret("too-short");

        Set<ConstraintViolation<AuthSecurityProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Event active crypto secret must be at least 32 bytes")));
    }

    @Test
    void whenEventCryptoPreviousKeyConfiguredButSecretMissingOrTooShortShouldFailValidation() {
        AuthSecurityProperties properties = createValidProperties();
        properties.getEventCrypto().setPreviousKeyId("crypto-v0");
        properties.getEventCrypto().setPreviousSecret("too-short");

        Set<ConstraintViolation<AuthSecurityProperties>> violations1 = validator.validate(properties);
        assertFalse(violations1.isEmpty());
        assertTrue(violations1.stream().anyMatch(v -> v.getMessage().contains("Event previous crypto secret must be at least 32 bytes if previous key is configured")));

        properties.getEventCrypto().setPreviousSecret("");
        Set<ConstraintViolation<AuthSecurityProperties>> violations2 = validator.validate(properties);
        assertFalse(violations2.isEmpty());
        assertTrue(violations2.stream().anyMatch(v -> v.getMessage().contains("Event previous crypto secret must be at least 32 bytes if previous key is configured")));
    }

    @Test
    void whenEventCryptoIsValidShouldPassValidation() {
        AuthSecurityProperties properties = createValidProperties();
        properties.getEventCrypto().setPreviousKeyId("crypto-v0");
        properties.getEventCrypto().setPreviousSecret("another-very-long-secret-key-at-least-32-bytes");

        Set<ConstraintViolation<AuthSecurityProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
    }

    @Test
    void whenEventCryptoActiveSecretIsMissingShouldFailValidation() {
        AuthSecurityProperties properties = createValidProperties();
        properties.getEventCrypto().setActiveSecret(null);

        Set<ConstraintViolation<AuthSecurityProperties>> violations1 = validator.validate(properties);
        assertFalse(violations1.isEmpty());

        properties.getEventCrypto().setActiveSecret("");
        Set<ConstraintViolation<AuthSecurityProperties>> violations2 = validator.validate(properties);
        assertFalse(violations2.isEmpty());
    }

    @Test
    void whenEventCryptoKeyIdsAreIdenticalShouldFailValidation() {
        AuthSecurityProperties properties = createValidProperties();
        properties.getEventCrypto().setPreviousKeyId(properties.getEventCrypto().getActiveKeyId());
        properties.getEventCrypto().setPreviousSecret("another-very-long-secret-key-at-least-32-bytes");

        Set<ConstraintViolation<AuthSecurityProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Event active key ID and previous key ID must not be identical")));
    }

    @Test
    void whenOauth2ExchangeCodeTtlIsNegativeOrZeroShouldFailValidation() {
        AuthSecurityProperties properties = createValidProperties();

        properties.getOauth2().setExchangeCodeTtl(java.time.Duration.ofSeconds(-5));
        Set<ConstraintViolation<AuthSecurityProperties>> violations1 = validator.validate(properties);
        assertFalse(violations1.isEmpty());
        assertTrue(violations1.stream().anyMatch(v -> v.getMessage().contains("OAuth2 exchange code TTL must be positive")));

        properties.getOauth2().setExchangeCodeTtl(java.time.Duration.ZERO);
        Set<ConstraintViolation<AuthSecurityProperties>> violations2 = validator.validate(properties);
        assertFalse(violations2.isEmpty());
        assertTrue(violations2.stream().anyMatch(v -> v.getMessage().contains("OAuth2 exchange code TTL must be positive")));
    }

    @Test
    void whenOauth2ExchangeCodeTtlIsValidShouldPass() {
        AuthSecurityProperties properties = createValidProperties();
        properties.getOauth2().setExchangeCodeTtl(java.time.Duration.ofSeconds(60));

        Set<ConstraintViolation<AuthSecurityProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
    }

    @Test
    void whenRateLimitWindowIsNegativeOrZeroShouldFailValidation() {
        AuthSecurityProperties properties = createValidProperties();

        properties.getRateLimit().getAnonymous().setWindow(java.time.Duration.ofSeconds(-5));
        Set<ConstraintViolation<AuthSecurityProperties>> violations1 = validator.validate(properties);
        assertFalse(violations1.isEmpty());
        assertTrue(violations1.stream().anyMatch(v -> v.getMessage().contains("Rate limit windows must be positive")));

        properties = createValidProperties();
        properties.getRateLimit().getAuthenticated().setWindow(java.time.Duration.ZERO);
        Set<ConstraintViolation<AuthSecurityProperties>> violations2 = validator.validate(properties);
        assertFalse(violations2.isEmpty());
        assertTrue(violations2.stream().anyMatch(v -> v.getMessage().contains("Rate limit windows must be positive")));
    }

    @Test
    void whenRateLimitCapacityIsZeroOrLessShouldFailValidation() {
        AuthSecurityProperties properties = createValidProperties();

        properties.getRateLimit().getAnonymous().setCapacity(0);
        Set<ConstraintViolation<AuthSecurityProperties>> violations1 = validator.validate(properties);
        assertFalse(violations1.isEmpty());

        properties = createValidProperties();
        properties.getRateLimit().getLogin().setCapacity(-10);
        Set<ConstraintViolation<AuthSecurityProperties>> violations2 = validator.validate(properties);
        assertFalse(violations2.isEmpty());
    }

    @Test
    void whenTrustedProxiesAreInvalidCidrShouldFailValidation() {
        AuthSecurityProperties properties = createValidProperties();
        properties.setTrustedProxies(List.of("192.168.1.1/invalid", "invalid-cidr"));

        Set<ConstraintViolation<AuthSecurityProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Trusted proxies must be valid CIDR expressions")));
    }

    @Test
    void whenTrustedProxiesAreValidCidrShouldPassValidation() {
        AuthSecurityProperties properties = createValidProperties();
        properties.setTrustedProxies(List.of("192.168.1.0/24", "10.0.0.0/8", "::1/128"));

        Set<ConstraintViolation<AuthSecurityProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
    }
}
