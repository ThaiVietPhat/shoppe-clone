package com.shopee.monolith.modules.inventory.dto.internal;

import lombok.Builder;

import java.util.UUID;

@Builder
public record InventoryStockSummary(
        UUID variantId,
        int availableStock,
        int reservedStock
) {}
