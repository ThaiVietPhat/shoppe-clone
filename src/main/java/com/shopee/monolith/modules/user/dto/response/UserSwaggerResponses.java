package com.shopee.monolith.modules.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public final class UserSwaggerResponses {

    private UserSwaggerResponses() {
    }

    @Schema(name = "ApiResponseCurrentUserResponse", description = "API response wrapper containing CurrentUserResponse")
    public record ApiResponseCurrentUserResponse(
            int code,
            String message,
            CurrentUserResponse data
    ) {
    }
}
