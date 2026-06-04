package com.shopee.monolith.modules.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
@Schema(description = "Request payload for email verification")
public record VerifyRequest(
        @NotBlank(message = "Token is required")
        @Schema(description = "Opaque verification token sent to email", example = "<opaque-verification-token>")
        String token
) {}
