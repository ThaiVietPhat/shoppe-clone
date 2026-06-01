package com.shopee.monolith.modules.auth.service;

import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;
import java.util.UUID;

public interface SessionRevocationService {
    void logout(String rawRefreshToken, AccessTokenClaims claims);
    void logoutAll(UUID userId);
}
