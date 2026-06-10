package com.shopee.monolith.modules.order.dto.internal;

import com.shopee.monolith.modules.order.model.CheckoutSessionStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Internal cross-module DTO exposing the payment-relevant view of a checkout session.
 * Consumed by PaymentModule — never exposed over HTTP.
 */
@Builder
public record CheckoutSessionPaymentInfo(
        UUID checkoutSessionId,
        UUID buyerId,
        CheckoutSessionStatus status,
        BigDecimal totalAmount,
        Instant expiresAt,
        List<UUID> orderIds
) {
}
