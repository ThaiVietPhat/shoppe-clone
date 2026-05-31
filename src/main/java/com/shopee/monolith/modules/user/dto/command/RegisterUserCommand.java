package com.shopee.monolith.modules.user.dto.command;

import lombok.Builder;

@Builder
public record RegisterUserCommand(
        String email,
        String passwordHash
) {}

