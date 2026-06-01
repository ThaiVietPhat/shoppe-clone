package com.shopee.monolith.modules.auth.service;

import com.shopee.monolith.modules.auth.entity.RefreshToken;
import com.shopee.monolith.modules.auth.repository.RefreshTokenRepository;
import com.shopee.monolith.modules.auth.security.RefreshTokenGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionRevocationWorker {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;

    @Transactional
    public void revokeFamily(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String tokenHash = refreshTokenGenerator.hash(rawRefreshToken);
        // Find the token to get familyId
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            // Lock the entire family stably to avoid race conditions with concurrent rotation
            List<RefreshToken> familyTokens = refreshTokenRepository.findAllByFamilyIdForUpdate(token.getFamilyId());
            if (!familyTokens.isEmpty()) {
                refreshTokenRepository.deleteByFamilyId(token.getFamilyId());
            }
        });
    }

    @Transactional
    public void revokeAll(UUID userId) {
        if (userId == null) {
            return;
        }
        // Lock user token rows stably in alphabetical/UUID ID order to avoid deadlocks
        List<RefreshToken> tokens = refreshTokenRepository.findAllByUserIdForUpdate(userId);
        if (!tokens.isEmpty()) {
            refreshTokenRepository.deleteByUserId(userId);
        }
    }
}
