package com.shopee.monolith.modules.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Module-specific response wrapper schemas for Swagger/OpenAPI documentation.
 * Keeps user/shop DTO references inside the business module scope,
 * ensuring the 'common' module has no reverse dependencies.
 */
public final class ShopSwaggerResponses {

    private ShopSwaggerResponses() {}

    @Schema(name = "ApiResponseShopResponse", description = "API response wrapper containing ShopResponse")
    public record ApiResponseShopResponse(
            @Schema(description = "Business or HTTP code", example = "200")
            int code,

            @Schema(description = "Message description of the operation outcome", example = "Success")
            String message,

            ShopResponse data
    ) {}
}
