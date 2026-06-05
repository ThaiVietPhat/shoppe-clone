package com.shopee.monolith.modules.cart.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
@Schema(description = "Payload to update an item's quantity in the shopping cart")
public record UpdateCartItemRequest(
        @Schema(description = "New quantity of the item (set to 0 to remove)", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Integer quantity
) {}
