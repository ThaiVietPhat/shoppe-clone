package com.shopee.monolith.modules.order.service;

import java.util.UUID;

/**
 * Port interface defined by OrderModule and implemented by PaymentModule.
 * Allows OrderModule to trigger payment-side side-effects on checkout cancellation
 * without creating a circular Spring bean dependency.
 */
public interface PaymentCancellationPort {

    /**
     * Expires all non-terminal payment attempts for the given checkout session.
     * Called within the same transaction as session cancellation so that
     * getPaymentStatus will no longer return PENDING or a VNPay redirect URL.
     * No locking is applied; if a webhook races and wins it will produce
     * REQUIRES_RECONCILIATION which is the correct outcome.
     */
    void expirePendingAttemptsForSession(UUID checkoutSessionId);
}
