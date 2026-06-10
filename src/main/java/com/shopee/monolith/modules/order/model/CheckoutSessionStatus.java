package com.shopee.monolith.modules.order.model;

public enum CheckoutSessionStatus {
    PENDING_PAYMENT,
    PAID,
    COMPLETED,
    PAYMENT_EXPIRED,
    CANCELLED,
    EXPIRED
}
