package com.shopee.monolith.modules.auth.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.config.JwtProperties;
import com.shopee.monolith.modules.auth.dto.internal.IssuedTokenPair;
import com.shopee.monolith.modules.auth.entity.RefreshToken;
import com.shopee.monolith.modules.auth.repository.RefreshTokenRepository;
import com.shopee.monolith.modules.auth.security.JwtTokenProvider;
import com.shopee.monolith.modules.auth.security.RefreshTokenGenerator;
import com.shopee.monolith.modules.user.model.Role;
import com.shopee.monolith.modules.user.service.UserService;
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
    private final UserService userService;
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

    @Transactional
    public IssuedTokenPair rotate(String rawRefreshToken) {
        String tokenHash = refreshTokenGenerator.hash(rawRefreshToken);
        RefreshToken currentToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN));

        Instant now = clock.instant();

        if (currentToken.getRevokedAt() != null) {
            revokeActiveFamilyTokens(currentToken.getFamilyId(), now);
            throw new AppException(ErrorCode.TOKEN_REUSE_DETECTED);
        }

        if (currentToken.getExpiresAt().isBefore(now)) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        String rawNewRefreshToken = refreshTokenGenerator.generate();
        String newRefreshTokenHash = refreshTokenGenerator.hash(rawNewRefreshToken);
        Instant newRefreshTokenExpiresAt = now.plus(jwtProperties.getRefreshExpiration());

        currentToken.revoke(now, newRefreshTokenHash);
        refreshTokenRepository.save(currentToken);

        saveRefreshToken(currentToken.getUserId(), newRefreshTokenHash,
                currentToken.getFamilyId(), newRefreshTokenExpiresAt);

        Role role = userService.getUserById(currentToken.getUserId()).role();
        String accessToken = jwtTokenProvider.generateAccessToken(currentToken.getUserId(), role);

        return IssuedTokenPair.builder()
                .accessToken(accessToken)
                .refreshToken(rawNewRefreshToken)
                .build();
    }

    private void revokeActiveFamilyTokens(UUID familyId, Instant now) {
        java.util.List<RefreshToken> familyTokens = refreshTokenRepository.findAllByFamilyIdForUpdate(familyId);
        for (RefreshToken token : familyTokens) {
            if (token.getRevokedAt() == null) {
                token.revoke(now);
                refreshTokenRepository.save(token);
            }
        }
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
