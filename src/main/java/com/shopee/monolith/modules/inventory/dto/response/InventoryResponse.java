package com.shopee.monolith.modules.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(description = "Represents inventory details of a variant")
public record InventoryResponse(
        @Schema(description = "Inventory unique ID")
        UUID id,

        @Schema(description = "Product variant UUID")
        UUID variantId,

        @Schema(description = "Quantity available for purchase", example = "45")
        int availableStock,

        @Schema(description = "Quantity locked in unpaid orders", example = "5")
        int reservedStock,

        @Schema(description = "Creation timestamp")
        Instant createdAt,

        @Schema(description = "Last update timestamp")
        Instant updatedAt
) {}
