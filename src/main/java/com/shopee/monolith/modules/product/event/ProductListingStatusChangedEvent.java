package com.shopee.monolith.modules.product.event;

import com.shopee.monolith.modules.product.entity.ProductStatus;

import java.util.UUID;

/**
 * Published when a product's listing status changes (ACTIVE, INACTIVE, DELETED).
 * Used for: cache eviction, notification triggers, audit trail.
 */
public record ProductListingStatusChangedEvent(
        UUID productId,
        UUID shopId,
        ProductStatus newStatus
) {}
