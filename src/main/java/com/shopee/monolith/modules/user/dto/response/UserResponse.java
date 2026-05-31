package com.shopee.monolith.modules.user.dto.response;

import com.shopee.monolith.modules.user.model.Role;
import lombok.Builder;

import java.util.UUID;

@Builder
public record UserResponse(
        UUID id,
        String email,
        Role role
) {
}
