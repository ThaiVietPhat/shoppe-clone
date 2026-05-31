package com.shopee.monolith.modules.user.dto.internal;

import com.shopee.monolith.modules.user.model.Role;
import com.shopee.monolith.modules.user.model.UserStatus;
import lombok.Builder;

import java.util.UUID;

@Builder
public record UserAuthenticationData(
        UUID id,
        String email,
        String passwordHash,
        Role role,
        UserStatus status
) {
}
