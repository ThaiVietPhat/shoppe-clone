package com.shopee.monolith.modules.auth.dto.internal;

import com.shopee.monolith.modules.user.model.Role;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record AccessTokenClaims(
        UUID userId,
        Role role,
        String jti,
        Instant issuedAt,
        Instant expiresAt
) {}
