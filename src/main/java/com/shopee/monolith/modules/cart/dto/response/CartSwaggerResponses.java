package com.shopee.monolith.modules.cart.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public final class CartSwaggerResponses {

    private CartSwaggerResponses() {}

    @Schema(name = "ApiResponseCartResponse", description = "API response wrapper containing shopping cart data")
    public record ApiResponseCartResponse(
            @Schema(description = "HTTP code", example = "200")
            int code,

            @Schema(description = "Message description", example = "Success")
            String message,

            CartResponse data
    ) {}
}
