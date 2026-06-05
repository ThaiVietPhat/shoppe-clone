package com.shopee.monolith.modules.cart.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
@Schema(description = "Details of a single item in the shopping cart")
public record CartItemResponse(
        @Schema(description = "ID of the product variant")
        UUID variantId,

        @Schema(description = "ID of the product")
        UUID productId,

        @Schema(description = "ID of the shop owning this product")
        UUID shopId,

        @Schema(description = "Name of the product variant")
        String name,

        @Schema(description = "SKU code of the product variant")
        String sku,

        @Schema(description = "Unit price of the product variant")
        BigDecimal price,

        @Schema(description = "Quantity of this variant in the cart")
        int quantity
) {}
