package com.shopee.monolith.modules.auth.security;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.config.JwtProperties;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.user.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenProviderTest {

    private static final String VALID_SECRET = "super-secret-key-that-is-at-least-64-bytes-long-for-testing-all-algorithms";
    private JwtProperties jwtProperties;
    private MutableClock clock;
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret(VALID_SECRET);
        jwtProperties.setExpiration(Duration.ofMinutes(10));
        jwtProperties.setRefreshExpiration(Duration.ofDays(7));

        clock = new MutableClock(Instant.ofEpochSecond(1700000000));
        jwtTokenProvider = new JwtTokenProvider(jwtProperties, clock);
    }

    @Test
    void generateAndParseShouldWorkCorrectly() {
        UUID userId = UUID.randomUUID();
        Role role = Role.BUYER;

        String token = jwtTokenProvider.generateAccessToken(userId, role);
        assertNotNull(token);

        AccessTokenClaims claims = jwtTokenProvider.parseAccessToken(token);
        assertEquals(userId, claims.userId());
        assertEquals(role, claims.role());
        assertNotNull(claims.jti());
        assertNotNull(claims.issuedAt());
        assertNotNull(claims.expiresAt());
    }

    @Test
    void parseTokenWithModifiedSignatureShouldThrowInvalidTokenException() {
        UUID userId = UUID.randomUUID();
        Role role = Role.BUYER;

        String token = jwtTokenProvider.generateAccessToken(userId, role);
        int lastDotIndex = token.lastIndexOf('.');
        String badToken = token.substring(0, lastDotIndex + 1) + "invalidSignature";

        AppException exception = assertThrows(AppException.class, () -> jwtTokenProvider.parseAccessToken(badToken));
        assertEquals(ErrorCode.INVALID_TOKEN, exception.getErrorCode());
    }

    @Test
    void parseTokenWithBlankJtiShouldThrowInvalidTokenException() {
        UUID userId = UUID.randomUUID();
        String tokenWithBlankJti = io.jsonwebtoken.Jwts.builder()
                .subject(userId.toString())
                .claim("role", Role.BUYER.name())
                .id("   ")
                .issuedAt(java.util.Date.from(clock.instant()))
                .expiration(java.util.Date.from(clock.instant().plus(Duration.ofMinutes(10))))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(VALID_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)), io.jsonwebtoken.Jwts.SIG.HS256)
                .compact();

        AppException exception = assertThrows(AppException.class, () -> jwtTokenProvider.parseAccessToken(tokenWithBlankJti));
        assertEquals(ErrorCode.INVALID_TOKEN, exception.getErrorCode());
    }

    @Test
    void parseExpiredTokenShouldThrowInvalidTokenException() {
        UUID userId = UUID.randomUUID();
        Role role = Role.BUYER;

        String token = jwtTokenProvider.generateAccessToken(userId, role);

        clock.advanceBy(Duration.ofMinutes(10).plusSeconds(1));

        AppException exception = assertThrows(AppException.class, () -> jwtTokenProvider.parseAccessToken(token));
        assertEquals(ErrorCode.INVALID_TOKEN, exception.getErrorCode());
    }

    @Test
    void getRemainingTtlShouldCalculateCorrectly() {
        UUID userId = UUID.randomUUID();
        Role role = Role.BUYER;

        String token = jwtTokenProvider.generateAccessToken(userId, role);

        clock.advanceBy(Duration.ofMinutes(3));

        Duration remainingTtl = jwtTokenProvider.getRemainingTtl(token);
        assertEquals(Duration.ofMinutes(7), remainingTtl);
    }

    @Test
    void getRemainingTtlForExpiredTokenShouldThrowInvalidTokenException() {
        UUID userId = UUID.randomUUID();
        Role role = Role.BUYER;

        String token = jwtTokenProvider.generateAccessToken(userId, role);

        clock.advanceBy(Duration.ofMinutes(10).plusSeconds(1));

        AppException exception = assertThrows(AppException.class, () -> jwtTokenProvider.getRemainingTtl(token));
        assertEquals(ErrorCode.INVALID_TOKEN, exception.getErrorCode());
    }

    @Test
    void secretTooShortShouldBeRejectedOnInstantiation() {
        JwtProperties shortSecretProps = new JwtProperties();
        shortSecretProps.setSecret("too-short");
        shortSecretProps.setExpiration(Duration.ofMinutes(10));
        shortSecretProps.setRefreshExpiration(Duration.ofDays(7));

        assertThrows(IllegalArgumentException.class, () -> new JwtTokenProvider(shortSecretProps, clock));
    }

    @Test
    void generateWithLongSecretShouldEnforceHs256() {
        String longSecret = "very-long-secret-key-that-exceeds-sixty-four-bytes-to-make-sure-jjwt-does-not-auto-choose-hs512-or-hs384-by-default";
        JwtProperties longSecretProps = new JwtProperties();
        longSecretProps.setSecret(longSecret);
        longSecretProps.setExpiration(Duration.ofMinutes(10));
        longSecretProps.setRefreshExpiration(Duration.ofDays(7));
        JwtTokenProvider providerWithLongSecret = new JwtTokenProvider(longSecretProps, clock);

        String token = providerWithLongSecret.generateAccessToken(UUID.randomUUID(), Role.BUYER);
        assertNotNull(token);

        io.jsonwebtoken.Jws<io.jsonwebtoken.Claims> jws = io.jsonwebtoken.Jwts.parser()
                .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(longSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .clock(() -> java.util.Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token);
        assertEquals("HS256", jws.getHeader().getAlgorithm());
    }

    @Test
    void parseTokenWithUnsupportedAlgorithmShouldThrowInvalidTokenException() {
        UUID userId = UUID.randomUUID();
        String tokenSignedWithHs512 = io.jsonwebtoken.Jwts.builder()
                .subject(userId.toString())
                .claim("role", Role.BUYER.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(java.util.Date.from(clock.instant()))
                .expiration(java.util.Date.from(clock.instant().plus(Duration.ofMinutes(10))))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(VALID_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)), io.jsonwebtoken.Jwts.SIG.HS512)
                .compact();

        AppException exception = assertThrows(AppException.class, () -> jwtTokenProvider.parseAccessToken(tokenSignedWithHs512));
        assertEquals(ErrorCode.INVALID_TOKEN, exception.getErrorCode());
    }

    private static class MutableClock extends Clock {
        private Instant now;

        public MutableClock(Instant start) {
            this.now = start;
        }

        public void advanceBy(Duration duration) {
            this.now = this.now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
