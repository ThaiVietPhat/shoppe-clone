package com.shopee.monolith.modules.product.event;

import java.util.UUID;

public record ProductUpdatedEvent(
        UUID productId,
        UUID shopId
) {}
