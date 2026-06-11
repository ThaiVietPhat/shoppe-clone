package com.shopee.monolith.modules.order.event;

import java.util.UUID;

/**
 * Published after a buyer cancels a checkout session and the cancel transaction commits.
 * PaymentModule listens AFTER_COMMIT to expire any non-terminal attempts for the session.
 */
public record CheckoutSessionCancelledEvent(UUID sessionId) {
}
