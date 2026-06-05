package com.shopee.monolith.modules.cart.dto.internal;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CartSnapshotItem(
        UUID variantId,
        int quantity
) {}
