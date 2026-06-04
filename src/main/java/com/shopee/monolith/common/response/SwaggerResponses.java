package com.shopee.monolith.common.response;

import com.shopee.monolith.modules.auth.dto.response.LoginResponse;
import com.shopee.monolith.modules.user.dto.response.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Static wrappers for Swagger/OpenAPI documentation to accurately document
 * the outer ApiResponse envelope structure with generic payloads.
 */
public final class SwaggerResponses {

    private SwaggerResponses() {}

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

    @Schema(name = "ApiResponseVoid", description = "API response wrapper with no data payload")
    public record ApiResponseVoid(
            @Schema(description = "Business or HTTP code", example = "200")
            int code,

            @Schema(description = "Message description of the operation outcome", example = "Success")
            String message
    ) {}
}
