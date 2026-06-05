package com.shopee.monolith.modules.product.event;

import java.util.UUID;

public record ProductCreatedEvent(
        UUID productId,
        UUID shopId
) {}
