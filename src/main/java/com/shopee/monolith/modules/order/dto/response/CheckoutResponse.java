package com.shopee.monolith.modules.order.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record CheckoutResponse(
        UUID checkoutSessionId,
        List<UUID> orderIds,
        String status,
        BigDecimal totalAmount,
        Instant expiresAt
) {}
