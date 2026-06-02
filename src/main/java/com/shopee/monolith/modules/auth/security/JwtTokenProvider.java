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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final Map<String, SecretKey> keys = new HashMap<>();

    public JwtTokenProvider(JwtProperties jwtProperties, Clock clock) {
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        for (Map.Entry<String, String> entry : jwtProperties.getKeyRing().getKeys().entrySet()) {
            this.keys.put(entry.getKey(), Keys.hmacShaKeyFor(entry.getValue().getBytes(StandardCharsets.UTF_8)));
        }
    }

    public String generateAccessToken(UUID userId, Role role) {
        Instant now = clock.instant();
        Instant exp = now.plus(jwtProperties.getExpiration());
        String activeKeyId = jwtProperties.getKeyRing().getActiveKeyId();
        SecretKey key = keys.get(activeKeyId);

        return Jwts.builder()
                .header().keyId(activeKeyId).and()
                .subject(userId.toString())
                .issuer(jwtProperties.getIssuer())
                .audience().add(jwtProperties.getAudience()).and()
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
                    .keyLocator(header -> {
                        String kid = (String) header.get("kid");
                        if (kid == null) {
                            return null;
                        }
                        return keys.get(kid);
                    })
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token);

            String alg = jws.getHeader().getAlgorithm();
            if (!"HS256".equals(alg)) {
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }

            Claims claims = jws.getPayload();

            String sub = claims.getSubject();
            String roleStr = claims.get("role", String.class);
            String jti = claims.getId();
            Date iat = claims.getIssuedAt();
            Date exp = claims.getExpiration();
            String iss = claims.getIssuer();
            var auds = claims.getAudience();

            if (sub == null || roleStr == null || jti == null || jti.isBlank() || iat == null || exp == null
                    || !jwtProperties.getIssuer().equals(iss)
                    || auds == null || !auds.contains(jwtProperties.getAudience())) {
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }

            UUID userId = UUID.fromString(sub);
            Role role = Role.valueOf(roleStr);

            return AccessTokenClaims.builder()
                    .userId(userId)
                    .role(role)
                    .jti(jti)
                    .issuedAt(iat.toInstant())
                    .expiresAt(exp.toInstant())
                    .build();

        } catch (JwtException | IllegalArgumentException e) {
            // Refrain from logging keys or client stack traces
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
