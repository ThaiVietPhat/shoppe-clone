package com.shopee.monolith.modules.auth.service;

import com.shopee.monolith.modules.auth.dto.internal.AccessTokenClaims;

public interface AccessTokenBlacklistService {
    void blacklist(AccessTokenClaims claims);
    boolean isBlacklisted(String jti);
}
