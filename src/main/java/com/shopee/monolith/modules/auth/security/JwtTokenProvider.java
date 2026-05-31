package com.shopee.monolith.modules.auth.security;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.config.JwtProperties;
import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import com.shopee.monolith.modules.user.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final SecretKey key;

    public JwtTokenProvider(JwtProperties jwtProperties, Clock clock) {
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        byte[] secretBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes long for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateAccessToken(UUID userId, Role role) {
        Instant now = clock.instant();
        Instant exp = now.plus(jwtProperties.getExpiration());

        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public AccessTokenClaims parseAccessToken(String token) {
        if (token == null || token.isBlank()) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token);

            String alg = jws.getHeader().getAlgorithm();
            if (!"HS256".equals(alg)) {
                log.debug("Rejected token signed with unsupported algorithm: {}", alg);
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }

            Claims claims = jws.getPayload();

            String sub = claims.getSubject();
            String roleStr = claims.get("role", String.class);
            String jti = claims.getId();
            Date iat = claims.getIssuedAt();
            Date exp = claims.getExpiration();

            if (sub == null || roleStr == null || jti == null || jti.isBlank() || iat == null || exp == null) {
                log.debug("Token is missing or has blank required claims: sub={}, role={}, jti={}, iat={}, exp={}",
                        sub, roleStr, jti, iat, exp);
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }

            UUID userId;
            try {
                userId = UUID.fromString(sub);
            } catch (IllegalArgumentException e) {
                log.debug("Token sub claim is not a valid UUID: {}", sub);
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }

            Role role;
            try {
                role = Role.valueOf(roleStr);
            } catch (IllegalArgumentException e) {
                log.debug("Token role claim is not a valid Role: {}", roleStr);
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }

            return AccessTokenClaims.builder()
                    .userId(userId)
                    .role(role)
                    .jti(jti)
                    .issuedAt(iat.toInstant())
                    .expiresAt(exp.toInstant())
                    .build();

        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Failed to parse access token: {}", e.getMessage());
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }

    public Duration getRemainingTtl(String token) {
        AccessTokenClaims claims = parseAccessToken(token);
        Instant now = clock.instant();
        if (claims.expiresAt().isBefore(now)) {
            return Duration.ZERO;
        }
        return Duration.between(now, claims.expiresAt());
    }
}
