package com.shopee.monolith.modules.product.dto.internal;

import lombok.Builder;

import java.util.UUID;

@Builder
public record ProductLookupData(
        UUID id,
        UUID shopId,
        UUID categoryId,
        String name
) {}
