package com.shopee.monolith.modules.order.service;

import com.shopee.monolith.modules.order.dto.internal.CheckoutSessionPaymentInfo;

import java.util.Optional;
import java.util.UUID;

/**
 * Internal contract used by PaymentModule to settle a checkout session after a
 * payment outcome. All mutating methods join the caller's transaction and lock
 * resources in a fixed order: checkout session → reservations → inventories
 * (variantId ASC) → orders (id ASC).
 */
public interface CheckoutSettlementService {

    Optional<CheckoutSessionPaymentInfo> findSessionPaymentInfo(UUID checkoutSessionId);

    /**
     * Payment success: confirm RESERVED reservations, deduct inventory,
     * transition orders to PAID and the session to COMPLETED.
     *
     * @return true if the session transitioned; false if it was no longer PENDING_PAYMENT (race lost — no-op)
     */
    boolean confirmCheckoutSession(UUID checkoutSessionId, String paymentMethod);

    /**
     * Payment attempt expired: release RESERVED reservations, cancel orders,
     * transition the session to PAYMENT_EXPIRED. Idempotent no-op when the session is terminal.
     */
    boolean markCheckoutPaymentExpired(UUID checkoutSessionId);

    /**
     * Payment failed at the provider: release RESERVED reservations, cancel orders,
     * transition the session to CANCELLED. Idempotent no-op when the session is terminal.
     */
    boolean markCheckoutPaymentFailed(UUID checkoutSessionId);
}
