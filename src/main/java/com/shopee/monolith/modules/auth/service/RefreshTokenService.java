package com.shopee.monolith.modules.auth.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.config.JwtProperties;
import com.shopee.monolith.modules.auth.dto.internal.IssuedTokenPair;
import com.shopee.monolith.modules.auth.dto.internal.RotationResult;
import com.shopee.monolith.modules.auth.entity.RefreshToken;
import com.shopee.monolith.modules.auth.entity.RefreshTokenFamily;
import com.shopee.monolith.modules.auth.repository.RefreshTokenFamilyRepository;
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
    private final RefreshTokenFamilyRepository familyRepository;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRotationWorker refreshTokenRotationWorker;
    private final Clock clock;

    @Transactional
    public IssuedTokenPair issueTokenPair(UUID userId, Role role) {
        // 1. Acquire pessimistic write lock on the user to prevent deadlocks and concurrent mutations
        userService.lockUser(userId);

        String accessToken = jwtTokenProvider.generateAccessToken(userId, role);
        String rawRefreshToken = refreshTokenGenerator.generate();
        String tokenHash = refreshTokenGenerator.hash(rawRefreshToken);
        Instant expiresAt = clock.instant().plus(jwtProperties.getRefreshExpiration());

        // 2. Create and persist parent RefreshTokenFamily row
        RefreshTokenFamily family = RefreshTokenFamily.builder()
                .userId(userId)
                .build();
        family = familyRepository.save(family);

        // 3. Link child refresh token to the family ID
        saveRefreshToken(userId, tokenHash, family.getId(), expiresAt);

        return IssuedTokenPair.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .build();
    }

    public IssuedTokenPair rotate(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        RotationResult result = refreshTokenRotationWorker.execute(rawRefreshToken);

        return switch (result) {
            case RotationResult.ReuseDetected reuse -> throw new AppException(ErrorCode.TOKEN_REUSE_DETECTED);
            case RotationResult.Rotated rotated -> rotated.tokenPair();
        };
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

    @Transactional
    public int deleteExpiredTokensBatch(Instant now, int batchSize) {
        // Delete expired families (which cascades deletes to their tokens)
        return familyRepository.deleteExpiredFamiliesBatch(now, batchSize);
    }
}
