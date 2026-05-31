package com.shopee.monolith.modules.auth.dto.internal;

import lombok.Builder;

@Builder
public record IssuedTokenPair(
        String accessToken,
        String refreshToken
) {}
