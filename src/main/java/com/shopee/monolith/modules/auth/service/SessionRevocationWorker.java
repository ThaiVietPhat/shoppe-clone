package com.shopee.monolith.modules.auth.service;

import com.shopee.monolith.modules.auth.entity.RefreshTokenFamily;
import com.shopee.monolith.modules.auth.repository.RefreshTokenFamilyRepository;
import com.shopee.monolith.modules.auth.repository.RefreshTokenRepository;
import com.shopee.monolith.modules.auth.security.RefreshTokenGenerator;
import com.shopee.monolith.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionRevocationWorker {

    private final RefreshTokenFamilyRepository familyRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final UserService userService;
    private final Clock clock;

    @Transactional
    public void revokeFamily(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String tokenHash = refreshTokenGenerator.hash(rawRefreshToken);
        refreshTokenRepository.findFamilyIdByTokenHash(tokenHash).ifPresent(familyId -> {
            // Lock the parent family aggregate
            familyRepository.findByIdForUpdate(familyId).ifPresent(family -> {
                Instant now = clock.instant();
                family.revoke(now);
                familyRepository.save(family);
                refreshTokenRepository.revokeActiveTokensInFamily(familyId, now);
            });
        });
    }

    @Transactional
    public void revokeAll(UUID userId) {
        if (userId == null) {
            return;
        }
        // 1. Lock user row first to prevent deadlock
        userService.lockUser(userId);

        // 2. Lock families in order of family.id ASC
        List<RefreshTokenFamily> families = familyRepository.findAllByUserIdForUpdate(userId);
        if (!families.isEmpty()) {
            Instant now = clock.instant();
            for (RefreshTokenFamily family : families) {
                family.revoke(now);
                familyRepository.save(family);
                refreshTokenRepository.revokeActiveTokensInFamily(family.getId(), now);
            }
        }
    }
}
