package com.shopee.monolith.modules.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Request payload for OAuth2 code exchange")
public record OAuth2ExchangeRequest(
        @NotBlank(message = "Code must not be blank")
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$", message = "Code must be a valid UUID format")
        @Schema(description = "One-time code received from OAuth2 redirect", example = "123e4567-e89b-12d3-a456-426614174000")
        String code
) {}
