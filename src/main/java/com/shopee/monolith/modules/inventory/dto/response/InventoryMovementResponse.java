package com.shopee.monolith.modules.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(name = "InventoryMovementResponse", description = "Audit ledger row for one stock movement")
public record InventoryMovementResponse(
        @Schema(description = "Movement unique ID")
        UUID id,

        @Schema(description = "Product variant UUID")
        UUID variantId,

        @Schema(description = "Movement type", example = "RESERVE",
                allowableValues = {"INITIAL", "STOCK_UPDATE", "RESERVE", "CONFIRM", "RELEASE"})
        String movementType,

        @Schema(description = "Positive magnitude for RESERVE/CONFIRM/RELEASE/INITIAL; signed delta for STOCK_UPDATE",
                example = "2")
        int quantity,

        @Schema(description = "Available stock after this movement", example = "8")
        int availableStockAfter,

        @Schema(description = "Reserved stock after this movement", example = "2")
        int reservedStockAfter,

        @Schema(description = "Movement timestamp")
        Instant createdAt
) {
}
