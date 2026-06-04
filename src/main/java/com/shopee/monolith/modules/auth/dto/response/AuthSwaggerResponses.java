package com.shopee.monolith.modules.auth.dto.response;

import com.shopee.monolith.modules.user.dto.response.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Module-specific response wrapper schemas for Swagger/OpenAPI documentation.
 * Keeps auth/user DTO references inside the business module scope,
 * ensuring the 'common' module has no reverse dependencies.
 */
public final class AuthSwaggerResponses {

    private AuthSwaggerResponses() {}

    @Schema(name = "ApiResponseLoginResponse", description = "API response wrapper containing LoginResponse")
    public record ApiResponseLoginResponse(
            @Schema(description = "Business or HTTP code", example = "200")
            int code,

            @Schema(description = "Message description of the operation outcome", example = "Success")
            String message,

            LoginResponse data
    ) {}

    @Schema(name = "ApiResponseUserResponse", description = "API response wrapper containing UserResponse")
    public record ApiResponseUserResponse(
            @Schema(description = "Business or HTTP code", example = "200")
            int code,

            @Schema(description = "Message description of the operation outcome", example = "Success")
            String message,

            UserResponse data
    ) {}
}
