package com.shopee.monolith.modules.product.dto.internal;

import lombok.Builder;

import java.util.UUID;

public record ProductStockSummaryDto(
        UUID variantId,
        int availableStock,
        int reservedStock
) {

    @Builder
    public ProductStockSummaryDto {
    }

    public static ProductStockSummaryDto empty(UUID variantId) {
        return new ProductStockSummaryDto(variantId, 0, 0);
    }
}
