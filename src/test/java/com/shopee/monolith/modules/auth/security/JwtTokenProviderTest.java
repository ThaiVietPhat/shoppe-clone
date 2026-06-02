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
import java.util.Map;
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
        jwtProperties.setIssuer("shoppe-monolith");
        jwtProperties.setAudience("shoppe-web-client");
        JwtProperties.KeyRingProperties keyRing = new JwtProperties.KeyRingProperties();
        keyRing.setActiveKeyId("key-v1");
        keyRing.setKeys(Map.of("key-v1", VALID_SECRET));
        jwtProperties.setKeyRing(keyRing);
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
                .header().keyId("key-v1").and()
                .subject(userId.toString())
                .issuer("shoppe-monolith")
                .audience().add("shoppe-web-client").and()
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
        shortSecretProps.setIssuer("shoppe-monolith");
        shortSecretProps.setAudience("shoppe-web-client");
        JwtProperties.KeyRingProperties keyRing = new JwtProperties.KeyRingProperties();
        keyRing.setActiveKeyId("key-v1");
        keyRing.setKeys(Map.of("key-v1", "too-short"));
        shortSecretProps.setKeyRing(keyRing);
        shortSecretProps.setExpiration(Duration.ofMinutes(10));
        shortSecretProps.setRefreshExpiration(Duration.ofDays(7));

        assertThrows(io.jsonwebtoken.security.WeakKeyException.class, () -> new JwtTokenProvider(shortSecretProps, clock));
    }

    @Test
    void generateWithLongSecretShouldEnforceHs256() {
        String longSecret = "very-long-secret-key-that-exceeds-sixty-four-bytes-to-make-sure-jjwt-does-not-auto-choose-hs512-or-hs384-by-default";
        JwtProperties longSecretProps = new JwtProperties();
        longSecretProps.setIssuer("shoppe-monolith");
        longSecretProps.setAudience("shoppe-web-client");
        JwtProperties.KeyRingProperties keyRing = new JwtProperties.KeyRingProperties();
        keyRing.setActiveKeyId("key-v1");
        keyRing.setKeys(Map.of("key-v1", longSecret));
        longSecretProps.setKeyRing(keyRing);
        longSecretProps.setExpiration(Duration.ofMinutes(10));
        longSecretProps.setRefreshExpiration(Duration.ofDays(7));
        JwtTokenProvider providerWithLongSecret = new JwtTokenProvider(longSecretProps, clock);

        String token = providerWithLongSecret.generateAccessToken(UUID.randomUUID(), Role.BUYER);
        assertNotNull(token);

        io.jsonwebtoken.Jws<io.jsonwebtoken.Claims> jws = io.jsonwebtoken.Jwts.parser()
                .keyLocator(header -> io.jsonwebtoken.security.Keys.hmacShaKeyFor(longSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .clock(() -> java.util.Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token);
        assertEquals("HS256", jws.getHeader().getAlgorithm());
    }

    @Test
    void parseTokenWithUnsupportedAlgorithmShouldThrowInvalidTokenException() {
        UUID userId = UUID.randomUUID();
        String tokenSignedWithHs512 = io.jsonwebtoken.Jwts.builder()
                .header().keyId("key-v1").and()
                .subject(userId.toString())
                .issuer("shoppe-monolith")
                .audience().add("shoppe-web-client").and()
                .claim("role", Role.BUYER.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(java.util.Date.from(clock.instant()))
                .expiration(java.util.Date.from(clock.instant().plus(Duration.ofMinutes(10))))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(VALID_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)), io.jsonwebtoken.Jwts.SIG.HS512)
                .compact();

        AppException exception = assertThrows(AppException.class, () -> jwtTokenProvider.parseAccessToken(tokenSignedWithHs512));
        assertEquals(ErrorCode.INVALID_TOKEN, exception.getErrorCode());
    }

    @Test
    void parseTokenSignedWithPreviousKeyShouldBeAccepted() {
        JwtProperties rotationProps = new JwtProperties();
        rotationProps.setIssuer("shoppe-monolith");
        rotationProps.setAudience("shoppe-web-client");
        JwtProperties.KeyRingProperties keyRing = new JwtProperties.KeyRingProperties();
        keyRing.setActiveKeyId("key-v2");
        keyRing.setKeys(Map.of(
                "key-v1", VALID_SECRET,
                "key-v2", "another-very-long-secret-key-at-least-64-bytes-long"
        ));
        rotationProps.setKeyRing(keyRing);
        rotationProps.setExpiration(Duration.ofMinutes(10));
        rotationProps.setRefreshExpiration(Duration.ofDays(7));
        JwtTokenProvider provider = new JwtTokenProvider(rotationProps, clock);

        String tokenPreviousKey = io.jsonwebtoken.Jwts.builder()
                .header().keyId("key-v1").and()
                .subject(UUID.randomUUID().toString())
                .issuer("shoppe-monolith")
                .audience().add("shoppe-web-client").and()
                .claim("role", Role.BUYER.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(java.util.Date.from(clock.instant()))
                .expiration(java.util.Date.from(clock.instant().plus(Duration.ofMinutes(10))))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(VALID_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)), io.jsonwebtoken.Jwts.SIG.HS256)
                .compact();

        AccessTokenClaims claims = provider.parseAccessToken(tokenPreviousKey);
        assertNotNull(claims);
    }

    @Test
    void parseTokenWithUnknownKidShouldThrowInvalidTokenException() {
        String tokenUnknownKid = io.jsonwebtoken.Jwts.builder()
                .header().keyId("key-unknown").and()
                .subject(UUID.randomUUID().toString())
                .issuer("shoppe-monolith")
                .audience().add("shoppe-web-client").and()
                .claim("role", Role.BUYER.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(java.util.Date.from(clock.instant()))
                .expiration(java.util.Date.from(clock.instant().plus(Duration.ofMinutes(10))))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(VALID_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)), io.jsonwebtoken.Jwts.SIG.HS256)
                .compact();

        AppException exception = assertThrows(AppException.class, () -> jwtTokenProvider.parseAccessToken(tokenUnknownKid));
        assertEquals(ErrorCode.INVALID_TOKEN, exception.getErrorCode());
    }

    @Test
    void parseTokenWithMissingKidShouldThrowInvalidTokenException() {
        String tokenMissingKid = io.jsonwebtoken.Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer("shoppe-monolith")
                .audience().add("shoppe-web-client").and()
                .claim("role", Role.BUYER.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(java.util.Date.from(clock.instant()))
                .expiration(java.util.Date.from(clock.instant().plus(Duration.ofMinutes(10))))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(VALID_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)), io.jsonwebtoken.Jwts.SIG.HS256)
                .compact();

        AppException exception = assertThrows(AppException.class, () -> jwtTokenProvider.parseAccessToken(tokenMissingKid));
        assertEquals(ErrorCode.INVALID_TOKEN, exception.getErrorCode());
    }

    @Test
    void parseTokenWithWrongIssuerShouldThrowInvalidTokenException() {
        String tokenWrongIssuer = io.jsonwebtoken.Jwts.builder()
                .header().keyId("key-v1").and()
                .subject(UUID.randomUUID().toString())
                .issuer("wrong-issuer")
                .audience().add("shoppe-web-client").and()
                .claim("role", Role.BUYER.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(java.util.Date.from(clock.instant()))
                .expiration(java.util.Date.from(clock.instant().plus(Duration.ofMinutes(10))))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(VALID_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)), io.jsonwebtoken.Jwts.SIG.HS256)
                .compact();

        AppException exception = assertThrows(AppException.class, () -> jwtTokenProvider.parseAccessToken(tokenWrongIssuer));
        assertEquals(ErrorCode.INVALID_TOKEN, exception.getErrorCode());
    }

    @Test
    void parseTokenWithWrongAudienceShouldThrowInvalidTokenException() {
        String tokenWrongAudience = io.jsonwebtoken.Jwts.builder()
                .header().keyId("key-v1").and()
                .subject(UUID.randomUUID().toString())
                .issuer("shoppe-monolith")
                .audience().add("wrong-audience").and()
                .claim("role", Role.BUYER.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(java.util.Date.from(clock.instant()))
                .expiration(java.util.Date.from(clock.instant().plus(Duration.ofMinutes(10))))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(VALID_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)), io.jsonwebtoken.Jwts.SIG.HS256)
                .compact();

        AppException exception = assertThrows(AppException.class, () -> jwtTokenProvider.parseAccessToken(tokenWrongAudience));
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
