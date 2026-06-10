package com.shopee.monolith.modules.order.model;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    CONFIRMED,
    FULFILLED,
    DELIVERED,
    COMPLETED,
    CANCELLED
}
