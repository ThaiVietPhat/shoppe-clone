package com.shopee.monolith.modules.product.dto.internal;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record VariantLookupData(
        UUID id,
        UUID productId,
        String sku,
        String name,
        BigDecimal price
) {}
