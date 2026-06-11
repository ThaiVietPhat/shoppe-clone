package com.shopee.monolith.modules.review.event;

import java.util.UUID;

/**
 * Published after a review is created or updated.
 * Listener recomputes the product rating read model AFTER_COMMIT.
 */
public record ReviewSubmittedEvent(UUID productId) {
}
