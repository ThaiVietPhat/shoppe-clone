package com.shopee.monolith.modules.order.event;

import java.util.UUID;

/**
 * Published after a seller fulfillment transition (SHIPPED / DELIVERED) commits.
 * NotificationModule uses it to write buyer inbox entries AFTER_COMMIT.
 */
public record OrderFulfillmentChangedEvent(
        UUID orderId,
        UUID buyerId,
        String fulfillmentStatus
) {
}
