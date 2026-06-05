package com.shopee.monolith.modules.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public final class InventorySwaggerResponses {

    private InventorySwaggerResponses() {}

    @Schema(name = "ApiResponseInventoryResponse", description = "API response wrapper containing InventoryResponse")
    public record ApiResponseInventoryResponse(
            int code,
            String message,
            InventoryResponse data
    ) {}
}
