package com.shopee.monolith.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record VerifyRequest(
        @NotBlank(message = "Token is required")
        String token
) {}
