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

    @Test
    void whenSameSiteIsNoneAndSecureIsFalseShouldFailValidation() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.getAuthCookie().setSameSite("None");
        properties.getAuthCookie().setSecure(false);

        Set<ConstraintViolation<AuthSecurityProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("SameSite=None requires Secure=true")));
    }

    @Test
    void whenSameSiteIsNoneAndSecureIsTrueShouldPassValidation() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.getAuthCookie().setSameSite("None");
        properties.getAuthCookie().setSecure(true);

        Set<ConstraintViolation<AuthSecurityProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty() || violations.stream().noneMatch(v -> v.getMessage().contains("SameSite=None requires Secure=true")));
    }

    @Test
    void whenCorsAllowedOriginsContainsWildcardWithCredentialsShouldFailValidation() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.getCors().setAllowCredentials(true);
        properties.getCors().setAllowedOrigins(List.of("http://localhost:3000", "*.domain.com"));

        Set<ConstraintViolation<AuthSecurityProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("CORS allowed origins must be valid URIs and cannot contain path or wildcards if allowCredentials is true")));
    }

    @Test
    void whenCorsAllowedOriginsContainsPathShouldFailValidation() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.getCors().setAllowCredentials(false);
        properties.getCors().setAllowedOrigins(List.of("http://localhost:3000/api"));

        Set<ConstraintViolation<AuthSecurityProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
    }

    @Test
    void whenCorsAllowedOriginsIsValidShouldPassValidation() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.getCors().setAllowCredentials(true);
        properties.getCors().setAllowedOrigins(List.of("http://localhost:3000", "https://app.shopee.com"));

        Set<ConstraintViolation<AuthSecurityProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty() || violations.stream().noneMatch(v -> v.getMessage().contains("CORS allowed origins must be valid URIs")));
    }

    @Test
    void whenVerificationTokenTtlIsNullShouldFailValidation() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.getVerificationToken().setTtl(null);

        Set<ConstraintViolation<AuthSecurityProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
    }

    @Test
    void whenVerificationTokenTtlIsNegativeOrZeroShouldFailValidation() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        
        properties.getVerificationToken().setTtl(java.time.Duration.ofSeconds(-5));
        Set<ConstraintViolation<AuthSecurityProperties>> violations1 = validator.validate(properties);
        assertFalse(violations1.isEmpty());
        assertTrue(violations1.stream().anyMatch(v -> v.getMessage().contains("Verification token TTL must be positive")));

        properties.getVerificationToken().setTtl(java.time.Duration.ZERO);
        Set<ConstraintViolation<AuthSecurityProperties>> violations2 = validator.validate(properties);
        assertFalse(violations2.isEmpty());
        assertTrue(violations2.stream().anyMatch(v -> v.getMessage().contains("Verification token TTL must be positive")));
    }
}
