package com.shopee.monolith.modules.cart.dto.internal;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record CartSnapshot(
        UUID userId,
        List<CartSnapshotItem> items,
        long version
) {}
