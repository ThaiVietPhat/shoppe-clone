package com.shopee.monolith.modules.cart.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(description = "Payload to add an item to the shopping cart")
public record AddCartItemRequest(
        @Schema(description = "ID of the product variant to add", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        UUID variantId,

        @Schema(description = "Quantity of the variant to add", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Min(value = 1, message = "Quantity must be greater than zero")
        Integer quantity
) {}
