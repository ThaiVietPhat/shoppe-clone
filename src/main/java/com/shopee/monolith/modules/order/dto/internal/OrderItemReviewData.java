package com.shopee.monolith.modules.order.dto.internal;

import lombok.Builder;

import java.util.UUID;

/**
 * Cross-module DTO for ReviewModule: snapshot of an order item plus
 * whether its parent order has reached a reviewable state.
 */
@Builder
public record OrderItemReviewData(
        UUID orderItemId,
        UUID orderId,
        UUID buyerId,
        UUID shopId,
        UUID variantId,
        String productName,
        String variantName,
        boolean reviewable
) {
}
