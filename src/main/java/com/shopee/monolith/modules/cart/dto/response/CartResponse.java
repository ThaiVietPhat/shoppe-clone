package com.shopee.monolith.modules.cart.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "Shopping cart details containing all active items and version state")
public record CartResponse(
        @Schema(description = "List of items in the cart")
        List<CartItemResponse> items,

        @Schema(description = "Total unique items in the cart")
        int totalItems,

        @Schema(description = "Current cart state version, incremented with each update")
        long version
) {}
