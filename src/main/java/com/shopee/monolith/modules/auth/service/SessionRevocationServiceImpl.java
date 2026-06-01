package com.shopee.monolith.modules.auth.service;

import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionRevocationServiceImpl implements SessionRevocationService {

    private final SessionRevocationWorker sessionRevocationWorker;
    private final AccessTokenBlacklistService accessTokenBlacklistService;

    @Override
    public void logout(String rawRefreshToken, AccessTokenClaims claims) {
        sessionRevocationWorker.revokeFamily(rawRefreshToken);
        if (claims != null) {
            accessTokenBlacklistService.blacklist(claims);
        }
    }

    @Override
    public void logoutAll(UUID userId) {
        sessionRevocationWorker.revokeAll(userId);
    }
}
