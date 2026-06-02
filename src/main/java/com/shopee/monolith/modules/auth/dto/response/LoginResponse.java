package com.shopee.monolith.modules.auth.dto.response;

import lombok.Builder;

@Builder
public record LoginResponse(
        String accessToken
) {}
