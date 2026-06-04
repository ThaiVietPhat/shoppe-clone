package com.shopee.monolith.modules.user.dto.internal;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record ShopLookupData(
        UUID id,
        UUID ownerId,
        String name,
        String description,
        BigDecimal rating
) {}
