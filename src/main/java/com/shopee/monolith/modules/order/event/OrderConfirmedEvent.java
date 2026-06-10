package com.shopee.monolith.modules.order.event;

import java.util.List;
import java.util.UUID;

/**
 * Published after payment success confirms all orders of a checkout session.
 * Listeners run AFTER_COMMIT only.
 */
public record OrderConfirmedEvent(
        UUID checkoutSessionId,
        List<UUID> orderIds,
        String paymentMethod
) {
}
