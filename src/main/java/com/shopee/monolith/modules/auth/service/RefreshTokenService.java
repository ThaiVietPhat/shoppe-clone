package com.shopee.monolith.modules.auth.service;

import com.shopee.monolith.modules.auth.config.JwtProperties;
import com.shopee.monolith.modules.auth.dto.internal.IssuedTokenPair;
import com.shopee.monolith.modules.auth.entity.RefreshToken;
import com.shopee.monolith.modules.auth.repository.RefreshTokenRepository;
import com.shopee.monolith.modules.auth.security.JwtTokenProvider;
import com.shopee.monolith.modules.auth.security.RefreshTokenGenerator;
import com.shopee.monolith.modules.user.model.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    @Transactional
    public IssuedTokenPair issueTokenPair(UUID userId, Role role) {
        String accessToken = jwtTokenProvider.generateAccessToken(userId, role);
        String rawRefreshToken = refreshTokenGenerator.generate();
        String tokenHash = refreshTokenGenerator.hash(rawRefreshToken);
        UUID familyId = UUID.randomUUID();
        Instant expiresAt = clock.instant().plus(jwtProperties.getRefreshExpiration());

        saveRefreshToken(userId, tokenHash, familyId, expiresAt);

        return IssuedTokenPair.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .build();
    }

    private void saveRefreshToken(UUID userId, String tokenHash, UUID familyId, Instant expiresAt) {
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .expiresAt(expiresAt)
                .build();
        refreshTokenRepository.save(refreshToken);
    }
}
