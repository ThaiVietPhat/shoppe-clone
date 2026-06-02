package com.shopee.monolith.modules.auth.service;

import com.shopee.monolith.common.exception.AppException;
import com.shopee.monolith.common.exception.ErrorCode;
import com.shopee.monolith.modules.auth.config.JwtProperties;
import com.shopee.monolith.modules.auth.dto.internal.IssuedTokenPair;
import com.shopee.monolith.modules.auth.dto.internal.RotationResult;
import com.shopee.monolith.modules.auth.entity.RefreshToken;
import com.shopee.monolith.modules.auth.repository.RefreshTokenRepository;
import com.shopee.monolith.modules.auth.security.JwtTokenProvider;
import com.shopee.monolith.modules.auth.security.RefreshTokenGenerator;
import com.shopee.monolith.modules.user.dto.internal.UserAuthenticationData;
import com.shopee.monolith.modules.user.model.UserStatus;
import com.shopee.monolith.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenRotationWorker {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final JwtProperties jwtProperties;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final Clock clock;

    @Transactional
    public RotationResult execute(String rawRefreshToken) {
        String tokenHash = refreshTokenGenerator.hash(rawRefreshToken);
        UUID familyId = refreshTokenRepository.findFamilyIdByTokenHash(tokenHash)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN));

        // Lock the entire family stably in order of token.id asc to unify locking protocol and prevent deadlocks
        java.util.List<RefreshToken> familyTokens = refreshTokenRepository.findAllByFamilyIdForUpdate(familyId);

        // Find the fresh/updated state of currentToken from the locked list
        RefreshToken lockedCurrentToken = familyTokens.stream()
                .filter(t -> t.getTokenHash().equals(tokenHash))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN));

        Instant now = clock.instant();

        if (lockedCurrentToken.getRevokedAt() != null) {
            refreshTokenRepository.revokeActiveTokensInFamily(familyId, now);
            return new RotationResult.ReuseDetected();
        }

        if (!lockedCurrentToken.getExpiresAt().isAfter(now)) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        UUID userId = lockedCurrentToken.getUserId();
        UserAuthenticationData userData = userService.findAuthenticationDataById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        validateUserStatus(userData.status());

        String rawNewRefreshToken = refreshTokenGenerator.generate();
        String newRefreshTokenHash = refreshTokenGenerator.hash(rawNewRefreshToken);
        Instant newRefreshTokenExpiresAt = now.plus(jwtProperties.getRefreshExpiration());

        String accessToken = jwtTokenProvider.generateAccessToken(userId, userData.role());

        lockedCurrentToken.revoke(now, newRefreshTokenHash);
        refreshTokenRepository.save(lockedCurrentToken);

        saveRefreshToken(userId, newRefreshTokenHash,
                lockedCurrentToken.getFamilyId(), newRefreshTokenExpiresAt);

        IssuedTokenPair tokenPair = IssuedTokenPair.builder()
                .accessToken(accessToken)
                .refreshToken(rawNewRefreshToken)
                .build();

        return new RotationResult.Rotated(tokenPair);
    }

    private void validateUserStatus(UserStatus status) {
        if (status == UserStatus.PENDING_VERIFICATION) {
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (status != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVE);
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
